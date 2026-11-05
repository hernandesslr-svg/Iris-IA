import argparse, json, random, time
from pathlib import Path
import torch
from model import IrisTransformer

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--steps',type=int,default=3000)
    ap.add_argument('--context',type=int,default=64)
    ap.add_argument('--batch-size',type=int,default=4)
    ap.add_argument('--output-dir',default='artifacts')
    args=ap.parse_args()
    torch.set_num_threads(4)
    torch.manual_seed(4242)
    random.seed(4242)
    out=Path(args.output_dir);out.mkdir(parents=True,exist_ok=True)
    pairs=json.loads(Path('dados/conversas_originais.json').read_text(encoding='utf-8'))
    assert len(pairs)>=20, 'Corpus too small'
    random.shuffle(pairs)
    cutoff=max(1,round(len(pairs)*.1))
    heldout=pairs[:cutoff];train=pairs[cutoff:]
    def encode(items):
        return torch.tensor(list((''.join('Usuário: '+x['user']+'\nIRIS: '+x['assistant']+'\n\n' for x in items)).encode('utf-8')),dtype=torch.long)
    tr=encode(train);va=encode(heldout)
    assert len(tr)>args.context+1 and len(va)>args.context+1
    model=IrisTransformer()
    optimizer=torch.optim.AdamW(model.parameters(),lr=0.00012,weight_decay=.01)
    start=time.time();losses=[];validation=[]
    def validate():
        model.eval()
        with torch.no_grad():
            vals=[]
            for j in range(0,len(va)-args.context-1,args.context):
                x=va[j:j+args.context][None,:]
                y=va[j+1:j+args.context+1][None,:]
                vals.append(float(model(x,y)[1]))
        model.train()
        return sum(vals)/len(vals)
    model.train()
    for step in range(1,args.steps+1):
        starts=torch.randint(0,len(tr)-args.context-1,(args.batch_size,))
        x=torch.stack([tr[j:j+args.context] for j in starts])
        y=torch.stack([tr[j+1:j+args.context+1] for j in starts])
        _,loss=model(x,y)
        optimizer.zero_grad(set_to_none=True)
        loss.backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(),1.0)
        optimizer.step()
        losses.append(float(loss))
        if step%250==0 or step==1:
            vl=validate();validation.append({'step':step,'train_loss':losses[-1],'heldout_loss':vl})
            print(json.dumps(validation[-1]),flush=True)
            torch.save({'model':model.state_dict(),'optimizer':optimizer.state_dict(),'step':step,'parameters':sum(p.numel() for p in model.parameters()),'train_examples':len(train),'heldout_examples':len(heldout)},out/'checkpoint.pt')
            (out/'metrics.json').write_text(json.dumps({'steps':step,'train_examples':len(train),'heldout_examples':len(heldout),'validation':validation,'elapsed_seconds':time.time()-start,'status':'experimental_not_complete'},indent=2),encoding='utf-8')
    model.eval()
    prompt='Usuário: Oi, tudo bem?\nIRIS: '
    tokens=torch.tensor([list(prompt.encode('utf-8'))],dtype=torch.long)
    with torch.no_grad():
        for _ in range(100):
            logits,_=model(tokens[:,-model.context:])
            nxt=logits[:,-1,:].argmax(-1,keepdim=True)
            tokens=torch.cat((tokens,nxt),dim=1)
    response=bytes(tokens[0].tolist()[len(prompt.encode('utf-8')):]).decode('utf-8','replace')
    (out/'sample.txt').write_text(response,encoding='utf-8')
    print('Sample:',repr(response),flush=True)

if __name__=='__main__':
    main()
