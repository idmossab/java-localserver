#!/usr/bin/env python3
import os
import sys

print("Content-Type: text/html")
print()
print("<html><body><h1>CGI is Working!</h1>")
print("<p>Request Method: " + os.environ.get('REQUEST_METHOD', 'Unknown') + "</p>")
print("<p>Query String: " + os.environ.get('QUERY_STRING', 'None') + "</p>")
print("<p>Server: " + os.environ.get('SERVER_NAME', 'Unknown') + "</p>")
print("<p>Python Version: " + sys.version + "</p>")
print("</body></html>")