#!/usr/bin/env python3
"""
使用OpenWeatherMap API查询天气的示例代码
需要先注册获取API密钥：https://openweathermap.org/api
"""

import requests
import json
import sys

def get_weather_by_city(city_name, api_key, units="metric"):
    """
    根据城市名称查询天气
    
    Args:
        city_name: 城市名称（如："Beijing"）
        api_key: OpenWeatherMap API密钥
        units: 温度单位，"metric"为摄氏度，"imperial"为华氏度
    
    Returns:
        天气数据的字典
    """
    base_url = "http://api.openweathermap.org/data/2.5/weather"
    
    params = {
        "q": city_name,
        "appid": api_key,
        "units": units,
        "lang": "zh_cn"  # 中文返回
    }
    
    try:
        response = requests.get(base_url, params=params, timeout=10)
        response.raise_for_status()
        return response.json()
    except requests.exceptions.RequestException as e:
        print(f"请求错误: {e}")
        return None
    except json.JSONDecodeError as e:
        print(f"JSON解析错误: {e}")
        return None

def get_weather_by_coords(lat, lon, api_key, units="metric"):
    """
    根据经纬度坐标查询天气
    
    Args:
        lat: 纬度
        lon: 经度
        api_key: OpenWeatherMap API密钥
        units: 温度单位
    
    Returns:
        天气数据的字典
    """
    base_url = "http://api.openweathermap.org/data/2.5/weather"
    
    params = {
        "lat": lat,
        "lon": lon,
        "appid": api_key,
        "units": units,
        "lang": "zh_cn"
    }
    
    try:
        response = requests.get(base_url, params=params, timeout=10)
        response.raise_for_status()
        return response.json()
    except requests.exceptions.RequestException as e:
        print(f"请求错误: {e}")
        return None
    except json.JSONDecodeError as e:
        print(f"JSON解析错误: {e}")
        return None

def display_weather(weather_data):
    """
    显示天气信息
    
    Args:
        weather_data: 天气数据字典
    """
    if not weather_data or "cod" in weather_data and weather_data["cod"] != 200:
        print("无法获取天气数据")
        if weather_data and "message" in weather_data:
            print(f"错误信息: {weather_data['message']}")
        return
    
    print("\n=== 天气信息 ===")
    print(f"城市: {weather_data.get('name', '未知')}")
    
    if "sys" in weather_data:
        country = weather_data["sys"].get("country", "")
        print(f"国家: {country}")
    
    if "main" in weather_data:
        main = weather_data["main"]
        print(f"温度: {main.get('temp', '未知')}°C")
        print(f"体感温度: {main.get('feels_like', '未知')}°C")
        print(f"最低温度: {main.get('temp_min', '未知')}°C")
        print(f"最高温度: {main.get('temp_max', '未知')}°C")
        print(f"湿度: {main.get('humidity', '未知')}%")
        print(f"气压: {main.get('pressure', '未知')} hPa")
    
    if "weather" in weather_data and len(weather_data["weather"]) > 0:
        weather = weather_data["weather"][0]
        print(f"天气状况: {weather.get('description', '未知')}")
        print(f"天气图标: http://openweathermap.org/img/wn/{weather.get('icon', '')}@2x.png")
    
    if "wind" in weather_data:
        wind = weather_data["wind"]
        print(f"风速: {wind.get('speed', '未知')} m/s")
        if "deg" in wind:
            print(f"风向: {wind.get('deg', '未知')}°")
    
    if "clouds" in weather_data:
        print(f"云量: {weather_data['clouds'].get('all', '未知')}%")
    
    if "visibility" in weather_data:
        visibility = weather_data["visibility"]
        if visibility >= 1000:
            print(f"能见度: {visibility/1000:.1f} km")
        else:
            print(f"能见度: {visibility} m")
    
    if "dt" in weather_data:
        from datetime import datetime
        dt = datetime.fromtimestamp(weather_data["dt"])
        print(f"数据时间: {dt.strftime('%Y-%m-%d %H:%M:%S')}")
    
    if "timezone" in weather_data:
        offset = weather_data["timezone"] / 3600
        print(f"时区: UTC{offset:+g}")

def main():
    """
    主函数：演示如何使用API
    """
    print("OpenWeatherMap API 天气查询示例")
    print("=" * 40)
    
    # 您需要在这里填写您的API密钥
    API_KEY = "YOUR_API_KEY_HERE"  # 请替换为您的实际API密钥
    
    if API_KEY == "YOUR_API_KEY_HERE":
        print("\n⚠️  请先注册OpenWeatherMap并获取API密钥：")
        print("   https://openweathermap.org/api")
        print("\n注册步骤：")
        print("1. 访问 https://openweathermap.org/api")
        print("2. 点击 'Subscribe' 或 'Sign Up'")
        print("3. 注册免费账户（Current Weather Data API 免费套餐每天可调用60次）")
        print("4. 在控制台获取您的API密钥")
        print("\n获取API密钥后，请替换代码中的 'YOUR_API_KEY_HERE'")
        return
    
    # 示例：查询北京的天气
    print("\n示例1：查询北京天气")
    weather_data = get_weather_by_city("Beijing", API_KEY)
    if weather_data:
        display_weather(weather_data)
    
    # 示例：使用经纬度查询（纽约的坐标）
    print("\n" + "=" * 40)
    print("示例2：使用经纬度查询纽约天气")
    weather_data = get_weather_by_coords(40.7128, -74.0060, API_KEY)
    if weather_data:
        display_weather(weather_data)

if __name__ == "__main__":
    main()