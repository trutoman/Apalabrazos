#!/bin/bash

BASE_URL="http://localhost:8080/api/register"

echo "1. Testing Invalid Username (too short)..."
curl -X POST $BASE_URL -H "Content-Type: application/json" -d '{"username": "ab", "email": "test@example.com", "password": "Password1"}'
echo -e "\n"

echo "2. Testing Invalid Email (bad format)..."
curl -X POST $BASE_URL -H "Content-Type: application/json" -d '{"username": "user", "email": "invalid", "password": "Password1"}'
echo -e "\n"

echo "3. Testing Invalid Password (too short)..."
curl -X POST $BASE_URL -H "Content-Type: application/json" -d '{"username": "user", "email": "test@example.com", "password": "Pass1"}'
echo -e "\n"

echo "4. Testing Invalid Password (no number)..."
curl -X POST $BASE_URL -H "Content-Type: application/json" -d '{"username": "user", "email": "test@example.com", "password": "Password"}'
echo -e "\n"

echo "5. Testing Valid Request..."
curl -X POST $BASE_URL -H "Content-Type: application/json" -d '{"username": "user", "email": "test@example.com", "password": "Password1"}'
echo -e "\n"
