import os
import sys
import argparse
from xor_crypto import XORCrypto

def do_directory(source_dir: str, suffix: str, dest_dir: str, key: str, mode: int) -> None:
    """Process all files with given suffix in source directory and its subdirectories.
    
    Args:
        source_dir: Source directory to search for files
        suffix: File suffix to match (e.g., '.txt')
        dest_dir: Destination directory for encrypted files
        key: Encryption key
    """
    # Create XORCrypto instance
    crypto = XORCrypto(key)
    
    # Create destination directory if it doesn't exist
    os.makedirs(dest_dir, exist_ok=True)
    
    # Walk through source directory
    for root, _, files in os.walk(source_dir):
        for file in files:
            if file.endswith(suffix):
                # Get full source path
                source_path = os.path.join(root, file)
                file_name = os.path.basename(source_path)
                
                # Ensure dest_dir ends with a slash
                if not dest_dir.endswith('/'):
                    dest_dir += '/'
                
                # Construct the encrypted file name
                if mode == 0:
                    encrypted_file_name = file_name + ".encrypted"
                else:
                    encrypted_file_name = file_name.replace(".encrypted", "")
                dest_path = os.path.join(dest_dir, encrypted_file_name)
                
                
                try:
                    # Encrypt file
                    if mode == 0:
                        crypto.encrypt_file(source_path, dest_path)
                    else:
                        crypto.decrypt_file(source_path, dest_path)
                    print(f"Encrypted: {source_path} -> {dest_path}")
                except Exception as e:
                    print(f"Error processing {source_path}: {str(e)}")

def do_single_file(source_path: str, dest_path: str, key: str, mode: int) -> None:
    crypto = XORCrypto(key)
    if mode == 0:  # encryption
        crypto.encrypt_file(source_path, dest_path)
    else:
        crypto.decrypt_file(source_path, dest_path)

def main():
    # Create argument parser
    parser = argparse.ArgumentParser(description='Encrypt files with given suffix in a directory')
    
    # Add arguments
    parser.add_argument('source_dir', help='Source directory to search for files')
    parser.add_argument('suffix', help='File suffix to match (e.g., .txt)')
    parser.add_argument('dest_dir', help='Destination directory for encrypted files')
    parser.add_argument('--key', help='Encryption key (optional, will generate random key if not provided)')
    parser.add_argument('--file', help='Encrypt file (or a directory)')
    parser.add_argument('--mode', help='Encryption mode (0: encryption, 1: decryption)')
    
    # Parse arguments
    args = parser.parse_args()
    
    # Generate random key if not provided
    if not args.key:
        args.key = XORCrypto.generate_random_key()
        print(f"Generated random key: {args.key}")
    
    # Process files
    try:
        mode = int(args.mode)
        if args.file == "1":
            print("Special case handling for file '1'")
            do_single_file(args.source_dir, args.dest_dir, args.key, mode)
        else:   
            do_directory(args.source_dir, args.suffix, args.dest_dir, args.key,mode)
        print("\nEncryption completed successfully!")
        print(f"Encryption key: {args.key}")
        print("Please save this key for decryption.")
    except Exception as e:
        print(f"Error: {str(e)}")
        sys.exit(1)

if __name__ == '__main__':
    main() 