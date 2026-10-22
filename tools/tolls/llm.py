import json,time,urllib.request,sys
BASE='https://www.llm.gov.my/Calculator/'
H={'User-Agent':'NavOS/0.1 toll table (contact: excellentmindblow@gmail.com)','X-Requested-With':'XMLHttpRequest','Referer':'https://www.llm.gov.my/'}
def get(path,tries=4):
    for t in range(tries):
        try:
            raw=urllib.request.urlopen(urllib.request.Request(BASE+path,headers=H),timeout=40).read().decode('utf-8-sig')
            time.sleep(0.25)
            return json.loads(raw) if raw.strip() else []
        except Exception as e:
            time.sleep(2**t)
    raise RuntimeError('failed '+path)
