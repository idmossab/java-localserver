#!/usr/bin/env python3
import os
import sys

# CGI Header
print("Content-Type: text/html")
print()

# HTML Output
print("<html>")
print("<head><title>CGI Test</title></head>")
print("<body>")
print("<h1>CGI is Working!</h1>")
print("<p>Request Method: " + os.environ.get('REQUEST_METHOD', 'Unknown') + "</p>")
print("<p>Query String: " + os.environ.get('QUERY_STRING', 'None') + "</p>")
print("<p>Server: " + os.environ.get('SERVER_NAME', 'Unknown') + "</p>")
print("<p>Python Version: " + sys.version + "</p>")
print("</body>")
print("</html>")