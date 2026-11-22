import os,sys,json
B={"assert","buffer","child_process","crypto","dns","events","fs","http","http2","https","net","os","path","process","querystring","stream","string_decoder","timers","tls","tty","url","util","vm","worker_threads","zlib"}
def needs(code):
    out=set();i=code.find("require(")
    while i>=0:
        j=i+8
        while j<len(code) and code[j]==' ': j+=1
        if j<len(code) and code[j] in "\"'`":
            q=code[j];e=code.find(q,j+1)
            if e>j and e-j<80:
                n=code[j+1:e]
                if n.startswith("node:"): n=n[5:]
                n=n.split('/')[0]
                if n in B: out.add(n)
        i=code.find("require(",i+8)
    return out
tot=ok=0
for repo in sys.argv[1:]:
    for root,_,fs in os.walk(repo):
        if '.git' in root or 'node_modules' in root or '/src' in root: continue
        for f in fs:
            if not f.endswith('.js'): continue
            p=os.path.join(root,f); c=open(p,errors='ignore').read()
            if 'getStreams' not in c: continue
            tot+=1; n=needs(c)
            if not n: ok+=1
print(f"scrapers={tot} no_node_builtins={ok}")
