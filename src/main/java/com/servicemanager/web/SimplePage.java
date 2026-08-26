package com.servicemanager.web;

/**
 * 内置极简原生面板页 — 零框架、零构建链的兜底前端
 * <p>
 * 背景：React 页面在老 WebView 中渲染正常但事件层失效（点击不产生任何网络请求），
 * 本页用原生 onclick + fetch 直调现有 REST API，作为默认首页。
 * 注意：文本块内的 JS/CSS 一律避免反斜杠转义；文本内容用 textContent 写入防注入。
 */
public final class SimplePage {

    private SimplePage() {
    }

    public static final String HTML = """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>服务管理面板</title>
            <style>
              html, body { margin: 0; background: #0a0e17; color: #c9d4e3;
                font-family: 'Microsoft YaHei', sans-serif; font-size: 13px; }
              .wrap { max-width: 1080px; margin: 0 auto; padding: 18px 20px 30px; }
              .top { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; }
              .title { font-size: 19px; font-weight: 700; flex: 1; }
              .sub { color: #8296b3; font-size: 12px; margin-top: 3px; }
              .btn { border: 1px solid #2b3549; background: #141b2b; color: #c9d4e3;
                border-radius: 6px; padding: 5px 14px; cursor: pointer; font-size: 13px; }
              .btn:hover { background: #1b2540; }
              .btn.primary { background: #2563eb; border-color: #2563eb; color: #fff; }
              .btn.danger { background: transparent; border-color: #e5484d; color: #ff8589; }
              .btn.ghost { background: transparent; border-color: #33415e; }
              .btn:disabled { opacity: .45; cursor: default; }
              #tip { min-height: 20px; margin: 0 0 8px; font-size: 13px; }
              .cards { display: flex; gap: 12px; margin-bottom: 14px; }
              .card { flex: 1; border: 1px solid #223052; border-radius: 10px;
                background: rgba(255,255,255,.03); padding: 10px 16px; }
              .num { font-size: 22px; font-weight: 700; line-height: 1.2; }
              .lbl { color: #8296b3; font-size: 12px; }
              table { width: 100%; border-collapse: collapse; }
              th, td { text-align: left; padding: 7px 10px; border-bottom: 1px solid #1a2440; }
              th { color: #8296b3; font-weight: normal; font-size: 12px; }
              tr:hover td { background: rgba(37,99,235,.06); }
              .mono { font-family: Consolas, monospace; }
              .grp { color: #8296b3; font-size: 11px; margin-top: 2px; }
              .dot { display: inline-block; width: 9px; height: 9px; border-radius: 50%;
                margin-right: 7px; vertical-align: middle; }
              .dot.ok { background: #34d399; box-shadow: 0 0 6px #34d399; }
              .dot.off { background: #4a5878; }
              .dot.warn { background: #fbbf24; }
              .dot.bad { background: #e5484d; }
              td.op button { margin-right: 6px; }
            </style>
            </head>
            <body>
            <div class="wrap">
              <div class="top">
                <div class="title">服务管理<span class="sub">每 3 秒自动刷新 · 同组服务联动启停 · 点击立即执行</span></div>
                <button class="btn" id="btnRefresh">刷新</button>
                <button class="btn primary" id="btnStartAll">全部启动</button>
                <button class="btn danger" id="btnStopAll">全部停止</button>
              </div>
              <div id="tip"></div>
              <div class="cards">
                <div class="card"><div class="num" id="stRun">-</div><div class="lbl">运行中</div></div>
                <div class="card"><div class="num" id="stStop">-</div><div class="lbl">已停止</div></div>
                <div class="card"><div class="num" id="stAbn">-</div><div class="lbl">异常 / 过渡</div></div>
                <div class="card"><div class="num" id="stAll">-</div><div class="lbl">服务总数</div></div>
              </div>
              <table>
                <thead>
                  <tr><th>状态</th><th>服务名</th><th>类型</th><th>端口</th><th>版本</th><th>PID</th><th>操作</th></tr>
                </thead>
                <tbody id="rows"></tbody>
              </table>
            </div>
            <script>
            var TOKEN = new URLSearchParams(location.search).get('token') || '';

            /** 统一请求：非 2xx 或 ok=false 抛错 */
            function req(path, opts) {
              var init = opts || {};
              init.headers = { 'X-Token': TOKEN };
              if (init.body) { init.headers['Content-Type'] = 'application/json'; }
              return fetch(path, init).then(function(r) {
                return r.json().then(function(d) {
                  if (!r.ok || d.ok === false) { throw new Error(d.error || ('请求失败 ' + r.status)); }
                  return d;
                });
              });
            }

            /** 行内提示条：bad=true 红色，4 秒自动清空 */
            function tip(msg, bad) {
              var el = document.getElementById('tip');
              el.textContent = msg || '';
              el.style.color = bad ? '#ff7a7a' : '#69db7c';
              clearTimeout(el._t);
              el._t = setTimeout(function() { el.textContent = ''; }, 4000);
            }

            var STATUS_META = {
              RUNNING: ['ok', '运行中'],
              STOPPED: ['off', '已停止'],
              STARTING: ['warn', '启动中'],
              STOPPING: ['warn', '停止中'],
              PORT_UNREACHABLE: ['bad', '端口不通'],
              UNKNOWN: ['bad', '未知']
            };

            /** 拉取列表并渲染 */
            function load() {
              return req('/api/services').then(render);
            }

            /** 提交动作后延迟刷一次，交给 3 秒轮询收尾 */
            function act(action, name, btn) {
              if (btn) { btn.disabled = true; }
              req('/api/services/action', {
                method: 'POST',
                body: JSON.stringify({ action: action, name: name || '' })
              }).then(function(d) {
                tip((name ? '「' + name + '」' : '') + (d.message || '任务已提交'));
                setTimeout(load, 1000);
              }).catch(function(e) {
                tip(e.message, true);
              }).then(function() {
                if (btn) { setTimeout(function() { btn.disabled = false; }, 800); }
              });
            }

            function openDir(name) {
              req('/api/services/open-dir', {
                method: 'POST',
                body: JSON.stringify({ name: name })
              }).then(function(d) { tip(d.message || '已打开'); })
                .catch(function(e) { tip(e.message, true); });
            }

            function td() { return document.createElement('td'); }

            function tdText(v, mono) {
              var c = td();
              c.textContent = v;
              if (mono) { c.className = 'mono'; }
              return c;
            }

            function render(d) {
              document.getElementById('stRun').textContent = d.running;
              document.getElementById('stStop').textContent = d.stopped;
              document.getElementById('stAbn').textContent = d.abnormal;
              document.getElementById('stAll').textContent = d.total;
              var tb = document.getElementById('rows');
              tb.textContent = '';
              d.services.forEach(function(s) {
                var meta = STATUS_META[s.status] || STATUS_META.UNKNOWN;
                var tr = document.createElement('tr');

                var st = td();
                var dot = document.createElement('span');
                dot.className = 'dot ' + meta[0];
                st.appendChild(dot);
                st.appendChild(document.createTextNode(meta[1]));
                tr.appendChild(st);

                var nm = td();
                var strong = document.createElement('strong');
                strong.textContent = s.name;
                nm.appendChild(strong);
                if (s.groupName) {
                  var g = document.createElement('div');
                  g.className = 'grp';
                  g.textContent = '分组: ' + s.groupName;
                  nm.appendChild(g);
                }
                tr.appendChild(nm);

                tr.appendChild(tdText(s.typeLabel));
                tr.appendChild(tdText(s.port > 0 ? String(s.port) : '-', true));
                tr.appendChild(tdText(s.version || '-', true));
                tr.appendChild(tdText(s.pid > 0 ? String(s.pid) : '-', true));

                var op = td();
                op.className = 'op';
                var running = s.status === 'RUNNING';
                var b = document.createElement('button');
                b.className = running ? 'btn danger' : 'btn primary';
                b.textContent = running ? '停止' : '启动';
                b.title = (running ? '停止（同组联动）：' : '启动（同组联动）：') + s.name;
                b.onclick = function() { act(running ? 'stop' : 'start', s.name, b); };
                op.appendChild(b);

                if (s.workingDir) {
                  var o = document.createElement('button');
                  o.className = 'btn ghost';
                  o.textContent = '目录';
                  o.title = '打开目录';
                  o.onclick = function() { openDir(s.name); };
                  op.appendChild(o);
                }
                tr.appendChild(op);

                tb.appendChild(tr);
              });
            }

            document.getElementById('btnRefresh').onclick = function() {
              load().then(function() { tip('已刷新'); })
                    .catch(function(e) { tip(e.message, true); });
            };
            document.getElementById('btnStartAll').onclick = function() { act('start-all', ''); this.blur(); };
            document.getElementById('btnStopAll').onclick = function() { act('stop-all', '', this); };

            load().catch(function(e) { tip(e.message, true); });
            setInterval(function() { load().catch(function() {}); }, 3000);
            </script>
            </body>
            </html>
            """;
}
