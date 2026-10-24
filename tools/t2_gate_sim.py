#!/usr/bin/env python3
"""Fluid model of the Tier 2 gate (resources/TierGate.java): when does the T2 lab finish for an eco rush and for a
normal player, before and after the gate? Mirrors EconomyServerEvents' flow economy (one global stall =
min(1, metal/demand, energy/demand), income banked to a 1500 cap) with nominal wind (14 energy/s a generator).

It is a model: the real timings come from the nightly bot ladder (T2 minute per bot) and the GameTest
bots_reach_tier_2_after_fielding_t1_units_and_not_before_minute_5. Run: python3 tools/t2_gate_sim.py
The numbers below must match TierGate.java; the PR table is this output.
"""
# fluid sim of the flow economy (dt=1s). Mirrors EconomyServerEvents: global stall = min(1, M/dM, E/dE).
WIND=14.0; CAP_M=2.0; CAP_E=20.0; MEX=2.0; MOHO=7.0
class Job:
    def __init__(s,name,m,e,work,kind='build'):
        s.name=name; s.m=m; s.e=e; s.work=work; s.done=0.0; s.kind=kind
def run(pol, P, horizon=900, verbose=False):
    t=0; M=1000.0; E=1000.0; stor=1500.0
    mex=0; winds=0; labs=0; moho=0
    workers=5; wq=0.0
    jobs=[]; queue=list(pol['order'])   # list of (name,cost m,e,work)
    log={}; army=0.0
    research=None  # progress
    t2=False; t2lab=False
    while t<horizon:
        # start jobs from queue (max concurrent)
        while queue and len([j for j in jobs if j.kind=='build'])<pol['sites']:
            q=queue[0]
            if q.get('after') and not q['after'](dict(t=t,mex=mex,winds=winds,labs=labs,t2=t2,army=army,M=M,E=E,t2lab=t2lab)): break
            queue.pop(0); jobs.append(Job(q['n'],q['m'],q['e'],q['w']))
        # research
        if research is None and not t2 and pol['research_when'](dict(t=t,mex=mex,winds=winds,labs=labs,t2=t2,army=army,M=M,E=E)):
            research=Job('T2',P['rm'],P['re'],P['rs'],'research'); jobs.append(research)
            log['research_start']=t
        # worker production at capitol (blocked during research)
        wantw=pol['workers'](t)
        if workers+ (1 if wq>0 else 0) < wantw and research is None and wq==0: wq=22.0
        # labs produce units
        incM=CAP_M+mex*MEX+moho*(MOHO)   # moho counted separately
        incE=CAP_E+winds*WIND
        upkeep=moho*P['moho_up']
        BP=P['eff']*(3+workers)
        # demands (per second)
        builds=[j for j in jobs if j.kind=='build']
        dM=0;dE=0
        share = BP/max(1,len(builds)) if builds else 0
        rates={}
        for j in builds:
            r=min(share, BP)/ j.work   # progress fraction per s
            rates[j]=r; dM+=j.m*r; dE+=j.e*r
        if research:
            r=1.0/research.work; rates[research]=r; dM+=research.m*r; dE+=research.e*r
        # units
        ulabs=min(labs,pol['unit_labs'](t)); uM=ulabs*P['unit_m']/P['unit_s']; uE=ulabs*P['unit_e']/P['unit_s']
        dM+=uM; dE+=uE
        if wq>0: dM+=50/22; dE+=70/22
        dE+=upkeep
        stallM = 1 if dM<=0 else min(1,M/dM) if M<dM else 1
        stallE = 1 if dE<=0 else min(1,E/dE) if E<dE else 1
        s=min(stallM,stallE)
        # moho efficiency from energy availability
        # advance
        for j,r in rates.items():
            j.done+=r*s
        M-= dM*s; E-= dE*s
        army+= uM*s
        if wq>0:
            wq-=s
            if wq<=0: workers+=1; wq=0
        # income
        effmoho = 1.0
        if upkeep>0:
            effmoho=min(1,(E+incE)/ (upkeep)) if E+incE<upkeep else 1
            E-=upkeep*s*0  # already counted in demand
        M=min(stor,M+incM- moho*MOHO*(1-effmoho)*0 )
        E=min(stor,E+incE)
        # completions
        for j in list(jobs):
            if j.done>=1.0:
                jobs.remove(j)
                n=j.name
                if n=='mex': mex+=1
                elif n=='wind': winds+=1
                elif n=='lab': labs+=1
                elif n=='T2': t2=True; log['t2_research_done']=t; research=None
                elif n=='t2lab': t2lab=True; log['t2_lab_done']=t
                elif n=='moho': mex-=1; moho+=1
        if t2 and not any(q['n']=='t2lab' for q in queue) and not t2lab and not any(j.name=='t2lab' for j in jobs):
            queue.insert(0,dict(n='t2lab',m=P['lm'],e=P['le'],w=P['lw']))
        if t2lab: break
        t+=1
    log['army_metal']=round(army); log['mex']=mex; log['winds']=winds; log['labs']=labs
    log['M_end']=round(M); log['E_end']=round(E)
    return log
def fm(x): return f"{x//60:.0f}:{x%60:02.0f}" if isinstance(x,(int,float)) else x
MEXJ=dict(n='mex',m=70,e=90,w=42); WINDJ=dict(n='wind',m=50,e=30,w=46); LABJ=dict(n='lab',m=45,e=220,w=123)
def order_rush(nm,nw):
    o=[WINDJ,MEXJ,MEXJ,WINDJ,MEXJ,MEXJ,LABJ,WINDJ,MEXJ,MEXJ,MEXJ]
    o=[dict(x) for x in o]
    o+= [dict(WINDJ) for _ in range(max(0,nw-3))]
    return o
def sc(rush_winds, normal_winds, P):
    out={}
    pol_rush=dict(order=order_rush(7,rush_winds),sites=2,workers=lambda t:8,
        research_when=lambda s: s['labs']>=1 and s['winds']>=rush_winds and s['mex']>=6,
        unit_labs=lambda t:0)
    out['rush']=run(pol_rush,P)
    # normal: lab first, 7 mex, 6 winds, 2nd/3rd labs, T2 decided at minute 4:30
    o=[LABJ,WINDJ,MEXJ,MEXJ,WINDJ,MEXJ,MEXJ,dict(LABJ),WINDJ,MEXJ,MEXJ,MEXJ,WINDJ,dict(LABJ)]+[dict(WINDJ) for _ in range(max(0,normal_winds-4))]
    pol_norm=dict(order=[dict(x) for x in o],sites=2,workers=lambda t:min(12,5+t//30),
        research_when=lambda s: s['t']>=P['decide'] and s['winds']>=normal_winds-0,
        unit_labs=lambda t:99)
    out['normal']=run(pol_norm,P)
    return out

if __name__=='__main__':
    base=dict(eff=0.6,unit_m=60,unit_e=85,unit_s=25,moho_up=0,decide=270)
    before=dict(base,rm=400,re=1200,rs=120,lm=245,le=220,lw=257)
    after=dict(base,rm=1000,re=8000,rs=120,lm=700,le=6000,lw=504)
    def row(tag,P,rw,nw):
        r=sc(rw,nw,P)
        for k,v in r.items():
            w=rw if k=='rush' else nw
            print('| %-6s | %-6s | %2d winds | research %s -> %s | lab done %s | army metal %d |'%(
                tag,k,w,fm(v.get('research_start')),fm(v.get('t2_research_done')),fm(v.get('t2_lab_done')),v['army_metal']))
    print('| gate   | player | energy   | research start -> done | T2 lab | T1 army spent |')
    row('before',before,5,6)
    for rw,nw in [(4,5),(7,7),(10,10),(14,14)]:
        row('after',after,rw,nw)
