"""Treinamento inicial da IRIS com corpus UTF-8 autorizado. Nao inclui corpus nem pesos."""
import argparse
import hashlib
from pathlib import Path
import torch
from model import IrisTransformer, encode, decode


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--corpus", required=True)
    parser.add_argument("--out", default="checkpoints")
    parser.add_argument("--steps", type=int, default=100)
    parser.add_argument("--resume", action="store_true")
    parser.add_argument("--batch", type=int, default=4)
    parser.add_argument("--context", type=int, default=128)
    parser.add_argument("--lr", type=float, default=3e-4)
    args = parser.parse_args()
    if args.steps < 1 or args.context < 2 or args.context > 256:
        parser.error("steps >= 1 e context entre 2 e 256")
    torch.manual_seed(42)
    corpus_bytes = Path(args.corpus).read_bytes()
    corpus_hash = hashlib.sha256(corpus_bytes).hexdigest()
    data = torch.tensor(list(corpus_bytes), dtype=torch.long)
    if len(data) <= args.context:
        parser.error("Corpus muito curto para o contexto escolhido")
    model = IrisTransformer()
    optimizer = torch.optim.AdamW(model.parameters(), lr=args.lr)
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    checkpoint = out / "iris-training.pt"
    initial_step = 0
    if args.resume:
        if not checkpoint.is_file():
            parser.error("Checkpoint inexistente")
        # Carregar apenas checkpoints locais criados por este script e confiaveis.
        state = torch.load(checkpoint, map_location="cpu", weights_only=True)
        if state.get("corpus_sha256") != corpus_hash:
            parser.error("Corpus mudou: nao retomar checkpoint com dados diferentes sem novo treinamento")
        model.load_state_dict(state["model"])
        optimizer.load_state_dict(state["optimizer"])
        initial_step = int(state["step"])
    print("Parametros:", sum(p.numel() for p in model.parameters()))
    model.train()
    for step in range(initial_step, initial_step + args.steps):
        starts = torch.randint(0, len(data) - args.context, (args.batch,))
        x = torch.stack([data[i:i + args.context] for i in starts])
        y = torch.stack([data[i + 1:i + args.context + 1] for i in starts])
        _, loss = model(x, y)
        optimizer.zero_grad(set_to_none=True)
        loss.backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
        optimizer.step()
        if (step + 1) % 10 == 0 or step == initial_step:
            print(f"step={step + 1} loss={loss.item():.4f}", flush=True)
    torch.save({"model": model.state_dict(), "optimizer": optimizer.state_dict(),
                "step": initial_step + args.steps, "corpus_sha256": corpus_hash}, checkpoint)
    model.eval()
    with torch.no_grad():
        sample = model.generate(torch.tensor([encode("Iris: ")], dtype=torch.long), max_new_tokens=32)
    print("Amostra (nao validada):", decode(sample[0].tolist()))


if __name__ == "__main__":
    main()
