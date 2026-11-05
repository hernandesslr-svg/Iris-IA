"""Prepara corpus de dialogos, removendo duplicatas exatas e quase identicas."""
import argparse
import hashlib
import json
import re
import unicodedata
from pathlib import Path


def normalized(text):
    text = unicodedata.normalize("NFKC", text).casefold()
    return re.sub(r"\s+", " ", text).strip()


def signature(example):
    return normalized(example["user"]) + "\n" + normalized(example["assistant"])


def prepare(source, destination):
    examples = json.loads(Path(source).read_text(encoding="utf-8"))
    if not isinstance(examples, list):
        raise ValueError("O corpus deve ser uma lista JSON")
    unique = {}
    for item in examples:
        if not isinstance(item, dict) or not isinstance(item.get("user"), str) or not isinstance(item.get("assistant"), str):
            raise ValueError("Cada dialogo exige user e assistant em texto")
        user, assistant = item["user"].strip(), item["assistant"].strip()
        if not user or not assistant:
            raise ValueError("Dialogos vazios nao sao permitidos")
        key = normalized(user)
        pair = signature({"user": user, "assistant": assistant})
        if key in unique:
            if unique[key][0] != pair:
                raise ValueError("Pergunta repetida com respostas conflitantes: " + user)
            continue
        unique[key] = (pair, user, assistant)
    output = "".join("Usuário: " + user + "\nIRIS: " + answer + "\n\n" for _, user, answer in unique.values())
    Path(destination).parent.mkdir(parents=True, exist_ok=True)
    Path(destination).write_text(output, encoding="utf-8")
    print(json.dumps({"original": len(examples), "unicos": len(unique), "duplicatas": len(examples)-len(unique),
                      "sha256": hashlib.sha256(output.encode("utf-8")).hexdigest()}, ensure_ascii=False))


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("--source", required=True)
    p.add_argument("--out", required=True)
    a = p.parse_args()
    prepare(a.source, a.out)
