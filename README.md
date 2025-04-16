# mi-file-crypto-tools

## 介绍

### 项目简介
 
对视频文件通过服务端ptyon脚本做加密，然后把加密后的视频文件放到云存储上。 在客户端（android,ios）使用声网播放器对加密后的视频文件做解密播放。 


### 媒体文件加密机制 

本项目实现了一套简单而高效的媒体文件加密和解密机制：

#### 文件加密流程

1. **加密头部生成**：在加密文件的开头添加16字节的头部信息，包含：
   - 魔数标识 (4字节)："MGPK"的十六进制表示
   - 版本号 (4字节)：用于兼容未来可能的格式变更
   - 原始文件大小 (8字节)：分高32位和低32位存储

2. **XOR加密算法**：使用简单高效的XOR加密算法对媒体数据进行加密
   - 支持自定义加密密钥
   - 根据数据偏移量进行加密，确保相同内容在不同位置有不同密文

3. **大文件流式处理**：
   - 小文件(<80KB)：直接加载到内存中加密
   - 大文件：使用流式加密，降低内存消耗

### 媒体文件播放方案

本项目实现了两种加密媒体文件的播放方案：

#### 本地加密文件播放

1. **自定义数据源**：
   - 通过`LocalDataReader`实现`IMediaPlayerCustomDataProviderBase`接口
   - 验证加密文件头部信息
   - 在播放过程中对读取的数据进行实时解密

2. **零拷贝解密**：
   - 在原数据缓冲区上直接进行解密操作(inplaceDecrypt)
   - 避免额外的内存分配和数据拷贝，提高性能

#### 在线加密媒体流播放

1. **缓存管理**：
   - 通过`CacheManager`管理媒体文件的缓存
   - 支持断点续传和部分文件缓存
   - 基于文件名和大小实现缓存识别

2. **流媒体数据处理**：
   - 通过`NetworkDataReader`实现网络数据的获取和解密
   - 支持HTTP/HTTPS协议的媒体流
   - 提供缓冲区管理和数据预取

3. **渐进式下载与播放**：
   - 边下载边播放，实现流畅的用户体验
   - 在播放过程中，根据用户操作(如跳转)智能调整下载策略

### 加密媒体解决方案优势

1. **安全性**：
   - 媒体内容被加密存储，未经授权无法直接播放
   - 支持在线和离线模式下的安全播放

2. **性能优化**：
   - 采用轻量级加密算法，解密开销小
   - 通过缓存机制减少重复下载
   - 实时解密不影响播放流畅度

3. **灵活性**：
   - 同时支持本地和网络媒体加密
   - 可自定义加密密钥和加密策略
   - 与声网RTC SDK无缝集成

## 项目结构 

该仓库主要包含服务端加密解密脚本，android和ios端的播放。 

目录  | 说明
------------- | -------------
/server  | 服务端加解密python脚本 
/ test  | 服务端加解密脚本测试的源视频及加密后的视频 
/ios    |  ios demo参考 
/android | android demo参考 



## 使用方式 

### 服务端

#### 加密 

命令 

```
python3 process_file.py /Users/zhangqi/Desktop/workZone/myproduct/mi-file-crypto-tools/test/in_mp4_files mp4 /Users/zhangqi/Desktop/workZone/myproduct/mi-file-crypto-tools/test/out_mp4_files/ --key key_key_key_key --file 0 --mode 0
```

加密成功

```
Encrypted: /Users/zhangqi/Desktop/workZone/myproduct/mi-file-crypto-tools/test/in_mp4_files/trailer.mp4 -> /Users/zhangqi/Desktop/workZone/myproduct/mi-file-crypto-tools/test/out_mp4_files/trailer.mp4.encrypted
Encrypted: /Users/zhangqi/Desktop/workZone/myproduct/mi-file-crypto-tools/test/in_mp4_files/oceans.mp4 -> /Users/zhangqi/Desktop/workZone/myproduct/mi-file-crypto-tools/test/out_mp4_files/oceans.mp4.encrypted

Encryption completed successfully!
Encryption key: key_key_key_key
Please save this key for decryption.
```

#### 解密 

命令

```
python3 process_file.py /Users/zhangqi/Desktop/workZone/myproduct/mi-file-crypto-tools/test/out_mp4_files encrypted /Users/zhangqi/Desktop/workZone/myproduct/mi-file-crypto-tools/test/out_mp4_files --key key_key_key_key --file 0 --mode 1
```

解密成功

```
Encrypted: /Users/zhangqi/Desktop/workZone/myproduct/mi-file-crypto-tools/test/out_mp4_files/oceans.mp4.encrypted -> /Users/zhangqi/Desktop/workZone/myproduct/mi-file-crypto-tools/test/out_mp4_files/oceans.mp4
Encrypted: /Users/zhangqi/Desktop/workZone/myproduct/mi-file-crypto-tools/test/out_mp4_files/trailer.mp4.encrypted -> /Users/zhangqi/Desktop/workZone/myproduct/mi-file-crypto-tools/test/out_mp4_files/trailer.mp4

Encryption completed successfully!
Encryption key: key_key_key_key
Please save this key for decryption.
```

### android

#### 运行指南 

在项目根目录下的 local.properties 文件中配置以下信息：

```
APP_ID=你的声网应用ID
APP_CERTIFICATE=你的声网证书密钥
```

在 Constants.kt 文件中的 KEY 常量表示加密秘钥：

```
// 文件路径: app/src/main/java/io/agora/mpk/test/utils/Constants.kt
object Constants {
    // 其他常量...
    
    // 媒体加密密钥，默认为"Agora-Media-Encryption"
    const val KEY = "你的新密钥"
}
```

### ios

#### 运行指南 

在 `RTCManager` 中设置你的appid 

```
    private enum Constants {
        static let appId = ""
    }
```

在 `NetworkDataReader`中添加你的加密秘钥： 

```
let key = "key_key_key_key"   // 本项目我测试的脚本使用的密钥是  key_key_key_key 
```

## 注意事项 

- 本项目中的加密方案适用于一般内容保护，不适用于高安全性要求的场景
- 加密后的媒体文件体积会略微增大（头部信息的16字节）
- 播放加密文件需要与加密时使用相同的密钥
- 更改全局加密密钥后，请确保所有相关组件都使用相同的新密钥
