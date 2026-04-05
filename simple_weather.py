#!/usr/bin/env python3
"""
简单的天气查询脚本
使用OpenWeatherMap API
"""

import requests
import sys

def get_weather(api_key, city="Beijing", units="metric"):
    """
    获取指定城市的天气
    """
    url = "http://api.openweathermap.org/data/2.5/weather"
    
    params = {
        "q": city,
        "appid": api_key,
        "units": units,
        "lang": "zh_cn"
    }
    
    try:
        response = requests.get(url, params=params, timeout=10)
        data = response.json()
        
        if data.get("cod") != 200:
            print(f"错误: {data.get('message', '未知错误')}")
            return None
            
        return data
    except Exception as e:
        print(f"请求失败: {e}")
        return None

def main():
    # 请在这里输入您的API密钥
    API_KEY = "YOUR_API_KEY_HERE"
    
    if API_KEY == "YOUR_API_KEY_HERE":
        print("请先获取OpenWeatherMap API密钥：")
        print("1. 访问 https://openweathermap.org/api")
        print("2. 注册免费账户")
        print("3. 获取API密钥并替换代码中的 'YOUR_API_KEY_HERE'")
        print("\n免费套餐：每天60次调用")
        return
    
    # 查询城市（可以修改为您所在的城市）
    city = input("请输入城市名称（英文，如 Beijing, Shanghai, New York）: ").strip()
    if not city:
        city = "Beijing"
    
    print(f"\n正在查询 {city} 的天气...")
    
    weather_data = get_weather(API_KEY, city)
    
    if weather_data:
        print(f"\n=== {weather_data['name']} 天气 ===")
        print(f"温度: {weather_data['main']['temp']}°C")
        print(f"体感温度: {weather_data['main']['feels_like']}°C")
        print(f"天气: {weather_data['weather'][0]['description']}")
        print(f"湿度: {weather_data['main']['humidity']}%")
        print(f"风速: {weather_data['wind']['speed']} m/s")
        print(f"气压: {weather_data['main']['pressure']} hPa")
    else:
        print("无法获取天气数据")

if __name__ == "__main__":
    main()