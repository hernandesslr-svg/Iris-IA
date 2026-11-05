"""Executa treinamento de conversacao com dados do projeto, sem adicionar respostas prontas ao app.
Uso: python iris-neural/train_conversation.py --steps 100
"""
import argparse
import subprocess
import sys
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--steps", type=int, default=100)
    parser.add_argument("--batch", type=int, default=4)
    parser.add_argument("--context", type=int, default=128)
    parser.add_argument("--out", default="checkpoints-conversa")
    parser.add_argument("--resume", action="store_true")
    args = parser.parse_args()
    root = Path(__file__).resolve().parent
    source = root / "dados" / "conversas_originais.json"
    prepared = root / "dados" / "conversas_treino.txt"
    subprocess.run([sys.executable, str(root / "prepare_dialogues.py"), "--source", str(source), "--out", str(prepared)], check=True)
    command = [sys.executable, str(root / "train.py"), "--corpus", str(prepared), "--steps", str(args.steps), "--batch", str(args.batch), "--context", str(args.context), "--out", args.out]
    if args.resume:
        command.append("--resume")
    subprocess.run(command, check=True)


if __name__ == "__main__":
    main()
