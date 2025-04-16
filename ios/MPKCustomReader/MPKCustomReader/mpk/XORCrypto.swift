import Foundation

class XORCrypto {
    private let key: [UInt8]
    
    init(key: [UInt8] = [0x12, 0x34, 0x56, 0x78, 0x9A, 0xBC, 0xDE, 0xF0]) {
        self.key = key
    }
    
    // MARK: - File Operations
    
    /// 加密文件
    /// - Parameters:
    ///   - sourceURL: 源文件路径
    ///   - destinationURL: 目标文件路径
    /// - Throws: 文件操作错误
    func encryptFile(at sourceURL: URL, to destinationURL: URL) throws {
        // 读取源文件
        let sourceData = try Data(contentsOf: sourceURL)
        
        // 加密数据
        let encryptedData = try encrypt(data: sourceData)
        
        // 写入目标文件
        try encryptedData.write(to: destinationURL, options: .atomic)
        
        Logger.log("Successfully encrypted file from \(sourceURL) to \(destinationURL)", className: "XORCrypto")
    }

    // MARK: - Data Operations
    
    func encrypt(data: Data) throws -> Data {
        // 创建header
        let header = CryptoHeader(fileSize: UInt64(data.count))
        let headerData = header.serialize()
        
        // 只加密数据部分，header保持明文
        let encrypted = data.enumerated().map { index, byte in
            byte ^ key[index % key.count]
        }
        
        // 组合header和加密后的数据
        var result = headerData
        result.append(Data(bytes: encrypted, count: encrypted.count))
        
        return result
    }
    
    func decrypt(data: Data, offset: Int) -> Data {
        if offset == 0 {
            // 确保数据长度足够
            guard data.count >= CryptoHeader.headerSize else {
                return data
            }
            
            // 解析header (不需要解密，因为header是明文)
            guard let header = CryptoHeader(data: data) else {
                return data
            }
            
            // 获取加密的实际数据部分
            let encryptedData = data.dropFirst(CryptoHeader.headerSize)
            
            // 解密数据部分
            let decrypted = encryptedData.enumerated().map { index, byte in
                byte ^ key[index % key.count]
            }
            
            return Data(bytes: decrypted, count: decrypted.count)
        } else {
            // 处理流式解密，需要考虑offset减去header大小
            let actualOffset = max(0, offset - CryptoHeader.headerSize)
            
            // 如果offset还在header区域内，不做任何解密
            if offset < CryptoHeader.headerSize {
                let headerRemaining = CryptoHeader.headerSize - offset
                if data.count <= headerRemaining {
                    return data
                } else {
                    // 部分header + 部分数据
                    var result = data.prefix(headerRemaining)
                    // 解密数据部分
                    let dataToDecrypt = data.dropFirst(headerRemaining)
                    let decrypted = dataToDecrypt.enumerated().map { index, byte in
                        byte ^ key[index % key.count]
                    }
                    result.append(Data(bytes: decrypted, count: decrypted.count))
                    return result
                }
            } else {
                // 完全是数据部分
                return Data(data.enumerated().map { index, byte in
                    byte ^ key[(actualOffset + index) % key.count]
                })
            }
        }
    }
    
    func getHeaderSize() -> Int {
        return CryptoHeader.headerSize
    }
} 
