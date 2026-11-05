"""Testes de sanidade da arquitetura IRIS; sem corpus externo."""
import unittest
import torch
from model import IrisTransformer, encode, decode


class IrisNeuralTests(unittest.TestCase):
    def test_utf8_roundtrip(self):
        text = "Olá, IRIS! Você está pronta?"
        self.assertEqual(decode(encode(text)), text)

    def test_forward_and_backward(self):
        torch.manual_seed(7)
        model = IrisTransformer(dim=32, layers=2, heads=4, ff_dim=128, context=16)
        x = torch.randint(0, 256, (2, 8))
        logits, loss = model(x, x)
        self.assertEqual(tuple(logits.shape), (2, 8, 256))
        self.assertTrue(torch.isfinite(loss).item())
        loss.backward()
        self.assertIsNotNone(model.token.weight.grad)

    def test_generation(self):
        torch.manual_seed(7)
        model = IrisTransformer(dim=32, layers=1, heads=4, ff_dim=128, context=16)
        result = model.generate(torch.tensor([encode("Oi")]), max_new_tokens=3)
        self.assertEqual(tuple(result.shape), (1, 5))

    def test_default_parameter_budget(self):
        model = IrisTransformer()
        count = sum(p.numel() for p in model.parameters())
        self.assertEqual(count, 9_999_338)


if __name__ == "__main__":
    unittest.main()
