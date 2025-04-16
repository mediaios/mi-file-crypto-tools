import struct
from typing import List, Optional
import logging
import os
import random
import string

class CryptoHeader:
    MAGIC_NUMBER = 0x4B50474D  # "MGPK" in hex
    VERSION = 1
    HEADER_SIZE = 16  # Total length 16 bytes
    
    def __init__(self, file_size: int):
        self.magic = self.MAGIC_NUMBER
        self.version = self.VERSION
        self.original_size = file_size
    
    @classmethod
    def from_bytes(cls, data: bytes) -> Optional['CryptoHeader']:
        """Create a CryptoHeader from bytes.
        
        Args:
            data: Bytes containing the header data
            
        Returns:
            CryptoHeader instance if valid, None otherwise
        """
        if len(data) < cls.HEADER_SIZE:
            return None
            
        # Read magic number (4 bytes)
        magic = struct.unpack('<I', data[0:4])[0]
        
        # Read version (4 bytes)
        version = struct.unpack('<I', data[4:8])[0]
        
        # Read original size (8 bytes)
        original_size = struct.unpack('<Q', data[8:16])[0]
        
        # Verify magic number
        if magic != cls.MAGIC_NUMBER:
            return None
            
        header = cls(original_size)
        return header
    
    def serialize(self) -> bytes:
        """Convert the header to bytes.
        
        Returns:
            Bytes representation of the header
        """
        # Pack magic number (4 bytes)
        magic_bytes = struct.pack('<I', self.magic)
        
        # Pack version (4 bytes)
        version_bytes = struct.pack('<I', self.version)
        
        # Pack original size (8 bytes)
        size_bytes = struct.pack('<Q', self.original_size)
        
        # Combine all bytes
        return magic_bytes + version_bytes + size_bytes

class XORCrypto:
    CHUNK_SIZE = 32 * 1024  # 32KB chunk size
    
    @staticmethod
    def generate_random_key(length: int = 16) -> str:
        """Generate a random string of specified length.
        
        Args:
            length: Length of the random string to generate (default: 16)
            
        Returns:
            Random string containing letters, digits, and special characters
        """
        # Define character sets
        letters = string.ascii_letters  # a-z, A-Z
        digits = string.digits  # 0-9
        special_chars = "!@#$%^&*()_+-=[]{}|;:,.<>?"  # Special characters
        
        # Combine all characters
        all_chars = letters + digits + special_chars
        
        # Generate random string
        random_string = ''.join(random.choice(all_chars) for _ in range(length))
        
        return random_string
    
    def __init__(self, key: str):
        self.key = key
    
    def encrypt_file(self, source_path: str, destination_path: str) -> None:
        """Encrypt a file using XOR encryption.
        
        Args:
            source_path: Path to the source file
            destination_path: Path to save the encrypted file
        """
        # Get file size
        file_size = os.path.getsize(source_path)
        
        # Create header with file size
        header = CryptoHeader(file_size)
        header_bytes = header.serialize()
        
        # Write header to destination file: means the heaeder is not encrypted
        with open(destination_path, 'wb') as dest_file:
            dest_file.write(header_bytes)
            
            # Read and encrypt file in chunks
            with open(source_path, 'rb') as source_file:
                offset = 0  # offset is 0, means the header is not encrypted
                while True:
                    chunk = source_file.read(self.CHUNK_SIZE)
                    if not chunk:
                        break
                    
                    # Encrypt chunk
                    encrypted_chunk = self.encrypt(chunk, offset)
                    dest_file.write(encrypted_chunk)
                    offset += len(chunk)
        
        logging.info(f"Successfully encrypted file from {source_path} to {destination_path}")
    
    def decrypt_file(self, source_path: str, destination_path: str) -> None:
        """Decrypt a file using XOR encryption.
        
        Args:
            source_path: Path to the encrypted file
            destination_path: Path to save the decrypted file
        """
        with open(source_path, 'rb') as source_file:
            # Read and verify header
            header_data = source_file.read(CryptoHeader.HEADER_SIZE)
            if len(header_data) < CryptoHeader.HEADER_SIZE:
                raise ValueError("Invalid encrypted file: missing header")
                
            header = CryptoHeader.from_bytes(header_data)
            if header is None:
                raise ValueError("Invalid encrypted file: invalid header")
            
            # Write decrypted data to destination file
            with open(destination_path, 'wb') as dest_file:
                offset = 0  # means the header is not encrypted
                remaining_size = header.original_size
                
                while remaining_size > 0:
                    # Read chunk (min of CHUNK_SIZE and remaining size)
                    chunk_size = min(self.CHUNK_SIZE, remaining_size)
                    encrypted_chunk = source_file.read(chunk_size)
                    if not encrypted_chunk:
                        break
                    
                    # Decrypt chunk
                    decrypted_chunk = self.decrypt(encrypted_chunk, offset)
                    dest_file.write(decrypted_chunk)
                    
                    offset += len(encrypted_chunk)
                    remaining_size -= len(decrypted_chunk)
                
                # Verify final size
                if remaining_size != 0:
                    raise ValueError(f"Invalid encrypted file: expected size {header.original_size}, got {header.original_size - remaining_size}")
        
        logging.info(f"Successfully decrypted file from {source_path} to {destination_path}")
    
    def encrypt(self, data: bytes, offset: int = 0, key: Optional[str] = None) -> bytes:
        """Encrypt data using XOR encryption.
        
        Args:
            data: Data to encrypt
            offset: Starting offset for key position calculation
            key: Optional custom key to use for encryption
            
        Returns:
            Encrypted data
        """
        key_to_use = key if key is not None else self.key
        
        # Calculate XOR for each byte
        encrypted = bytes(
            byte ^ ord(key_to_use[(offset + i) % len(key_to_use)])
            for i, byte in enumerate(data)
        )
        
        return encrypted
    
    def decrypt(self, data: bytes, offset: int = 0, key: Optional[str] = None) -> bytes:
        """Decrypt data using XOR encryption.
        
        Args:
            data: Data to decrypt
            offset: Starting offset for key position calculation
            key: Optional custom key to use for decryption
            
        Returns:
            Decrypted data
        """
        # XOR encryption is symmetric, so decryption is the same as encryption
        return self.encrypt(data, offset, key)
    
    def get_header_size(self) -> int:
        """Get the size of the crypto header.
        
        Returns:
            Size of the header in bytes
        """
        return CryptoHeader.HEADER_SIZE 
    
     