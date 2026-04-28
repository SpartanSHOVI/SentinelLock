#!/usr/bin/env python3
"""
Minimal model_prep.py placeholder matching README instructions.
Real script should convert a SavedModel to TFLite and optionally calibrate against LFW.
"""
import argparse
import os
import sys


def convert_model(model_path, output_dir):
    # This is a placeholder. Real conversion uses TensorFlow TFLiteConverter.
    print(f"Pretending to convert model at {model_path} to {output_dir}")
    os.makedirs(output_dir, exist_ok=True)
    dest = os.path.join(output_dir, "mobilefacenet.tflite")
    with open(dest, "wb") as f:
        f.write(b"\n")
    print("Wrote placeholder tflite to", dest)


def calibrate(lfw_dir):
    print(f"Pretending to calibrate against LFW at {lfw_dir}")
    print("Recommended DISTANCE_THRESHOLD: 0.90")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model_path", help="Path to SavedModel directory")
    parser.add_argument("--output_dir", help="Output directory for tflite", default="../app/src/main/assets/")
    parser.add_argument("--calibrate", action="store_true")
    parser.add_argument("--lfw_dir", help="Path to LFW dataset for calibration")

    args = parser.parse_args()
    if args.calibrate:
        if not args.lfw_dir:
            print("--lfw_dir required when --calibrate is set", file=sys.stderr)
            sys.exit(2)
        calibrate(args.lfw_dir)
    else:
        if not args.model_path:
            print("--model_path is required for conversion", file=sys.stderr)
            sys.exit(2)
        convert_model(args.model_path, args.output_dir)


if __name__ == "__main__":
    main()
