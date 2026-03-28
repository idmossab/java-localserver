#!/usr/bin/env python3
import os
import sys

print("Content-Type: application/json")
print("Status: 201 Internal Server Error")
print()
print(f'{{"message": "Hello from CGI script!", "method": "{os.environ.get("REQUEST_METHOD", "Unknown")}"}}')