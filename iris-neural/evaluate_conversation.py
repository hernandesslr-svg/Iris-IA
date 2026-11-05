"""Evaluate actual IRIS checkpoint with diverse unseen prompts; no simulated answers."""
import json, torch
from pathlib import Path
from model import IrisTransformer

QUESTIONS = [
 ("saudacao","Oi, IRIS! Como você está?"),
 ("identidade","Qual é o seu nome?"),
 ("aritmetica","Quanto é 2 + 2?"),
 ("aritmetica","Quanto é 15 vezes 3?"),
 ("conhecimento","Qual é a capital do Brasil?"),
 ("conhecimento","Por que o céu é azul?"),
 ("portugues","Explique o que significa amizade."),
 ("instrucao","Responda apenas com a palavra azul."),
 ("logica","Se Ana tem 3 maçãs e ganha 2, quantas tem?"),
 ("seguranca","O que devo fazer se alguém desmaiar?"),
 ("incerteza","Qual será o resultado da loteria amanhã?"),
 ("criatividade","Crie uma frase curta sobre a lua."),
 ("contexto","Meu nome é Pedro. Qual é meu nome?"),
 ("conversa","Estou triste hoje. Pode conversar comigo?"),
 ("traducao","Traduza good morning para português.")
]
def main():
    path=Path("artifacts/checkpoint.pt")
    state=torch.load(path,map_location="cpu",weights_only=False)
    model=IrisTransformer()
    model.load_state_dict(state["model"])
    model.eval()
    print("CHECKPOINT_STEP:",state.get("step"),flush=True)
    results=[]
    for category,question in QUESTIONS:
        prompt=f"Usuário: {question}\nIRIS: "
        raw=prompt.encode("utf-8")
        tokens=torch.tensor([list(raw)],dtype=torch.long)
        with torch.inference_mode():
            generated=model.generate(tokens,max_new_tokens=100,temperature=0.7)
        output=bytes(generated[0].tolist()[len(raw):]).decode("utf-8","replace").split("\n")[0].strip()
        row={"category":category,"question":question,"answer":output}
        results.append(row)
        print(json.dumps(row,ensure_ascii=False),flush=True)
    Path("artifacts/evaluation.json").write_text(json.dumps({"checkpoint_step":state.get("step"),"results":results},ensure_ascii=False,indent=2),encoding="utf-8")
if __name__=="__main__":
    main()
