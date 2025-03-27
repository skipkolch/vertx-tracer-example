import http.server
import socketserver
import socket
from urllib.parse import urlparse, parse_qs
import json
import threading


NET_SERVER_HOST = 'localhost'
NET_SERVER_PORT = 8081


class ThreadedTCPServer(socketserver.ThreadingMixIn, socketserver.TCPServer):
    daemon_threads = True
    allow_reuse_address = True


class HttpGetHandler(http.server.SimpleHTTPRequestHandler):
    def do_GET(self):
        parsed_url = urlparse(self.path)
        query_params = parse_qs(parsed_url.query)
        id_value = query_params.get('id', [None])[0]

        if not id_value:
            self.send_error(400, "Missing 'id' parameter")
            return

        print(f"[{threading.current_thread().name}] Processing ID: {id_value}")

        try:
            payload = {
                "id": str(id_value),
                "request": f"GET {self.path}"
            }
            json_data = json.dumps(payload).encode('utf-8')

            with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as net_socket:
                net_socket.settimeout(100.0)  # Таймаут соединения
                net_socket.connect((NET_SERVER_HOST, NET_SERVER_PORT))
                net_socket.sendall(json_data)
                response = net_socket.recv(4096).decode('utf-8')

                self.send_response(200)
                self.send_header('Content-type', 'application/json')
                self.end_headers()
                self.wfile.write(response.encode('utf-8'))

        except socket.timeout:
            self.send_error(504, "Net-server timeout")
        except ConnectionRefusedError:
            self.send_error(502, "Net-server unavailable")
        except Exception as e:
            print(f"Error: {e}")
            self.send_error(500, "Internal Server Error")


def run_server():
    server = ThreadedTCPServer(("", 8080), HttpGetHandler)
    print(f"Starting server on port 8080")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down server...")
        server.shutdown()


if __name__ == "__main__":
    run_server()