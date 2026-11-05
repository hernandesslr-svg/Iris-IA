"""Modelo causal Transformer IRIS, inicializado aleatoriamente, sem pesos externos."""
import torch
from torch import nn
import torch.nn.functional as F


class IrisTransformer(nn.Module):
    def __init__(self, vocab_size=256, dim=256, layers=6, heads=8, context=256, ff_dim=1024):
        super().__init__()
        self.context = context
        self.token = nn.Embedding(vocab_size, dim)
        self.position = nn.Embedding(context, dim)
        self.blocks = nn.ModuleList([
            nn.TransformerEncoderLayer(
                d_model=dim, nhead=heads, dim_feedforward=ff_dim,
                dropout=0.0, activation="gelu", batch_first=True, norm_first=True
            ) for _ in range(layers)
        ])
        self.norm = nn.LayerNorm(dim)
        self.head = nn.Linear(dim, vocab_size, bias=False)
        self.head.weight = self.token.weight

    def forward(self, tokens, targets=None):
        batch, length = tokens.shape
        if length > self.context:
            raise ValueError("Sequencia maior que a janela de contexto")
        positions = torch.arange(length, device=tokens.device)
        x = self.token(tokens) + self.position(positions)[None, :, :]
        mask = torch.triu(torch.ones(length, length, dtype=torch.bool, device=tokens.device), diagonal=1)
        for block in self.blocks:
            x = block(x, src_mask=mask)
        logits = self.head(self.norm(x))
        loss = F.cross_entropy(logits.reshape(-1, logits.shape[-1]), targets.reshape(-1)) if targets is not None else None
        return logits, loss

    @torch.no_grad()
    def generate(self, tokens, max_new_tokens=64, temperature=0.8):
        self.eval()
        for _ in range(max_new_tokens):
            logits, _ = self(tokens[:, -self.context:])
            probs = F.softmax(logits[:, -1, :] / max(temperature, 1e-4), dim=-1)
            next_token = torch.multinomial(probs, 1)
            tokens = torch.cat((tokens, next_token), dim=1)
        return tokens


def encode(text):
    return list(text.encode("utf-8"))


def decode(tokens):
    return bytes(tokens).decode("utf-8", errors="replace")
