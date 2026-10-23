import json, os, threading
from concurrent.futures import ThreadPoolExecutor

exec(open('llm.py').read())
D = json.load(open('enters.json'))
done = set()
if os.path.exists('rates.jsonl'):
    for line in open('rates.jsonl'):
        r = json.loads(line)
        done.add((r['hw'], r['enter'], r['exit']))
exits_cache = json.load(open('exits.json')) if os.path.exists('exits.json') else {}
jobs = []
for h in D['highways']:
    hw = str(h['id'])
    for e in D['enters'][hw]:
        key = f"{hw}/{e['id']}"
        if key not in exits_cache:
            exits_cache[key] = [x for x in get(f"getPlazaExit/{hw}/{e['id']}") if str(x.get('id', '')) != '']
            json.dump(exits_cache, open('exits.json', 'w'), ensure_ascii=False)
        ex = exits_cache[key]
        targets = [x['id'] for x in ex if x['id'] != e['id']] if ex else ['null']
        for x in targets:
            if (hw, e['id'], x) not in done:
                jobs.append((hw, e['id'], x))
print('todo', len(jobs), flush=True)
lock = threading.Lock()
out = open('rates.jsonl', 'a')
n = [0]


def work(j):
    hw, en, x = j
    try:
        rates = get(f"getTollRate/{hw}/{en}/{x}")
    except Exception as err:
        print('ERR', j, err, flush=True)
        return
    code = 2 if hw == '5' else 1  # car fare; Jambatan Pulau Pinang's class 1 is motorcycle
    car = [r['rate'] for r in rates if r.get('transCode') == code]
    with lock:
        out.write(json.dumps({'hw': hw, 'enter': en, 'exit': x, 'car': car[0] if car else None}) + '\n')
        out.flush()
        n[0] += 1
        if n[0] % 500 == 0:
            print('progress', n[0], 'of', len(jobs), flush=True)


with ThreadPoolExecutor(4) as pool:
    list(pool.map(work, jobs))
print('DONE', n[0], flush=True)
