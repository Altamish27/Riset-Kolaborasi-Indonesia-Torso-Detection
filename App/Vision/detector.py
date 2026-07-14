import os
from pathlib import Path
from dotenv import load_dotenv
from PIL import Image, UnidentifiedImageError
from google import genai

def detect_single_organ(image_path: str) -> str:
    """
    Detects a single human torso organ from an image using the Gemini API.
    
    Args:
        image_path: The path to the image file.
        
    Returns:
        The name of the detected organ as a pure string.
    """
    # 1. Load Environment Variables
    load_dotenv()
    api_key = os.getenv("GEMINI_API_KEY")
    
    if not api_key:
        raise ValueError("GEMINI_API_KEY environment variable is missing. Please set it in your .env file.")
        
    # 2. Image Input Handling
    path = Path(image_path)
    if not path.is_file():
        raise FileNotFoundError(f"The image file was not found at: {image_path}")
        
    try:
        image = Image.open(image_path)
    except UnidentifiedImageError:
        raise ValueError(f"The file at {image_path} is not a valid or supported image format.")
    except Exception as e:
        raise RuntimeError(f"Error opening image: {e}")

    # 3. Gemini API Client Initialization
    try:
        client = genai.Client(api_key=api_key)
    except Exception as e:
        raise RuntimeError(f"Failed to initialize Gemini Client: {e}")

    # 4. Payload Structure & 5. Strict Output Enforcement
    prompt = (
        "Identifikasi organ torso tunggal yang ada pada gambar ini. "
        "Pilih HANYA dari salah satu pilihan berikut:\n"
        "- Otak\n- Kepala\n- Tenggorokan\n- Dada_Luar\n- Dada_Dalam\n- Rusuk\n"
        "- Paru-Paru_Kanan\n- Paru-paru_Kiri\n- Jantung\n- Ginjal_Luar\n"
        "- Ginjal_Dalam\n- Hati\n- Lambung\n- Usus\n- Penis\n- Vagina\n\n"
        "Aturan Khusus untuk Ginjal: Jika gambar organ ginjal tersebut memiliki banyak warna merah, pilih 'Ginjal_Dalam'. Jika memiliki banyak warna hitam, pilih 'Ginjal_Luar'.\n\n"
        "Berikan output HANYA berupa nama organ dari daftar tersebut persis seperti yang tertulis (termasuk huruf kapital dan underscore). "
        "Jangan sertakan kalimat pembuka, penjelasan, atau format markdown apa pun. "
        "Jika tidak yakin, pilih yang paling mendekati dari daftar tersebut."
    )
    
    try:
        response = client.models.generate_content(
            model='gemini-2.5-flash-lite',
            contents=[image, prompt],
        )
        # Strip whitespace and return the pure string
        return response.text.strip()
    except Exception as e:
        raise RuntimeError(f"API call failed: {e}")

if __name__ == "__main__":
    # Main execution block to easily test the function
    # Example usage (update the path with a real image for testing)
    test_image_path = "Usus.png"
    
    # Create a dummy image for testing purposes if it doesn't exist
    if not os.path.exists(test_image_path):
        print(f"Creating a dummy '{test_image_path}' for testing...")
        dummy_img = Image.new('RGB', (100, 100), color = 'red')
        dummy_img.save(test_image_path)
        print("Remember to replace this with an actual torso organ image.")
        
    print(f"Running detection on {test_image_path}...")
    try:
        result = detect_single_organ(test_image_path)
        print(f"Detection Result: {result}")
    except Exception as e:
        print(f"Error: {e}")
