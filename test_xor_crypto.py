import unittest
import os
import tempfile
from xor_crypto import XORCrypto

class TestXORCrypto(unittest.TestCase):
    def setUp(self):
        """Set up test fixtures."""
        key = XORCrypto.generate_random_key()
        self.crypto = XORCrypto(key)
        self.test_data = b"Hello, World! This is a test message."
        self.temp_dir = tempfile.mkdtemp()
        print(f"Key: {key}")
    
    def tearDown(self):
        """Clean up test fixtures."""
        # Clean up temporary files
        for file in os.listdir(self.temp_dir):
            os.remove(os.path.join(self.temp_dir, file))
        os.rmdir(self.temp_dir)
    
    def test_encrypt_decrypt_data(self):
        """Test basic encryption and decryption of data."""
        # Encrypt data
        offset = 0
        encrypted = self.crypto.encrypt(self.test_data, offset)
        
        # Decrypt data
        decrypted = self.crypto.decrypt(encrypted, offset)
        
        # Verify the decrypted data matches the original
        self.assertEqual(decrypted, self.test_data)
    
    def test_encrypt_decrypt_file(self):
        """Test file encryption and decryption."""
        # Create temporary files
        source_path = os.path.join(self.temp_dir, "source.txt")
        encrypted_path = os.path.join(self.temp_dir, "encrypted.txt")
        decrypted_path = os.path.join(self.temp_dir, "decrypted.txt")
        
        # Write test data to source file
        with open(source_path, "wb") as f:
            f.write(self.test_data)
        
        # Encrypt file
        self.crypto.encrypt_file(source_path, encrypted_path)
        
        # Read encrypted file and decrypt
        self.crypto.decrypt_file(encrypted_path, decrypted_path)
        
        
        
        # Verify the decrypted file matches the original
        with open(decrypted_path, "rb") as f:
            self.assertEqual(f.read(), self.test_data)
    
    def test_streaming_decryption(self):
        """Test streaming decryption with offset."""
        # Encrypt data
        encrypted = self.crypto.encrypt(self.test_data)
        
      
        # Split data into chunks and decrypt with offset
        chunk_size = 5
        decrypted_chunks = []
        
       
        
        # Then handle the rest of the data
        for i in range(0, len(encrypted), chunk_size):
            chunk = encrypted[i:i + chunk_size]
            decrypted_chunk = self.crypto.decrypt(chunk, offset=i)
            decrypted_chunks.append(decrypted_chunk)
        
        # Combine decrypted chunks
        decrypted = b"".join(decrypted_chunks)
        
       
        
        # Verify the decrypted data matches the original
        self.assertEqual(decrypted, self.test_data)
    
    def test_custom_key(self):
        """Test encryption with custom key."""
        custom_key = "123abcegkV&*()_+-=[]{}|;:,.<>?"
        custom_crypto = XORCrypto(key=custom_key)
        
        # Encrypt with custom key
        encrypted = custom_crypto.encrypt(self.test_data)
        
        # Decrypt with same key
        decrypted = custom_crypto.decrypt(encrypted)
        
        # Verify decryption works with custom key
        self.assertEqual(decrypted, self.test_data)
        
        # Verify decryption fails with default key
        decrypted_with_default = self.crypto.decrypt(encrypted)
        self.assertNotEqual(decrypted_with_default, self.test_data)
    
    def test_empty_data(self):
        """Test encryption and decryption of empty data."""
        empty_data = b""
        
        # Encrypt empty data
        encrypted = self.crypto.encrypt(empty_data)
        
        # Decrypt empty data
        decrypted = self.crypto.decrypt(encrypted)
        
        # Verify empty data handling
        self.assertEqual(decrypted, empty_data)

if __name__ == '__main__':
    unittest.main() 