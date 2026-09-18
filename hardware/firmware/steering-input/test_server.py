from http.server import HTTPServer, BaseHTTPRequestHandler
import json

class H(BaseHTTPRequestHandler):
    def do_POST(self):
        data = self.rfile.read(int(self.headers['Content-Length']))
        print(json.dumps(json.loads(data), indent=2))
        self.send_response(200)
        self.end_headers()
    def log_message(self, *a): pass

print("Test server luistert op http://localhost:5000")
HTTPServer(('', 5000), H).serve_forever()
