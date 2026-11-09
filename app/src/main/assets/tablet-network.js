'use strict';
/* Tablet-only opt-in visual layer. No network probes, SSH commands or private defaults.
   Results must be supplied by an explicitly authorized diagnostic adapter. */
(function(){
  if(window.TCTabletView) return;
  document.body.classList.add('tablet-industrial');
  const $=id=>document.getElementById(id);
  const names=['CC T1','CC T2','FFAA1','RTU','SABT T1','SABT T2'];
  const screens=['home','detail','weekly','work','config','help','feedback','reminders'];
  const state={mango:'unchecked',router:'unchecked',tablet:'unchecked',equipment:names.map(name=>({name,status:'unchecked',ip:'',rtt:null})),current:'home'};
  const escape=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  function sketch(kind){
    if(kind==='tablet') return '<svg viewBox="0 0 180 120" role="img" aria-label="Tablet Android"><rect x="24" y="8" width="132" height="101" rx="11" fill="#252525" stroke="#111" stroke-width="4"/><rect x="32" y="15" width="116" height="84" rx="3" fill="#263a55"/><path d="M57 78L88 32l22 46z" fill="none" stroke="#fff" stroke-width="3"/><circle cx="90" cy="103" r="2" fill="#dedede"/></svg>';
    if(kind==='mango') return '<svg viewBox="0 0 120 85" role="img" aria-label="Router GL.iNet Mango amarillo estilo sketch"><path d="M12 38L30 14h65l15 24-3 32-16 9H27L10 63Z" fill="#ffdb35" stroke="#1a1a1a" stroke-width="3"/><path d="M12 38h98M28 38l2 39M91 39l1 37" fill="none" stroke="#363636" stroke-width="2"/><rect x="34" y="47" width="18" height="16" fill="#292929"/><rect x="59" y="47" width="18" height="16" fill="#292929"/><path d="M39 47v11m6-11v11m20-11v11m6-11v11" stroke="#aaa"/><circle cx="89" cy="51" r="3" fill="#111"/><path d="M30 17l62 1" stroke="#fff5b1" stroke-width="4"/></svg>';
    return '<svg viewBox="0 0 180 120" role="img" aria-label="Router del CT gris estilo sketch"><path d="M12 48L37 22h111l20 26v45l-24 12H36L12 89Z" fill="#bdbfc0" stroke="#292929" stroke-width="3"/><path d="M12 48h156M36 48v57M144 48v57" stroke="#4a4a4a" stroke-width="2"/><rect x="45" y="59" width="25" height="24" rx="2" fill="#292929"/><rect x="75" y="59" width="25" height="24" rx="2" fill="#292929"/><rect x="105" y="59" width="25" height="24" rx="2" fill="#292929"/><circle cx="144" cy="70" r="4" fill="#999"/><path d="M37 25h110" stroke="#ececec" stroke-width="4"/></svg>';
  }
  const nav=document.createElement('nav');nav.id='tablet-topnav';nav.setAttribute('aria-label','Navegación de TELECONTROL TABLET');
  const title=document.createElement('strong');title.textContent='TELECONTROL';nav.appendChild(title);
  const tabs=[['home','INICIO'],['detail','FICHA CT'],['network','PING / RED'],['weekly','TRABAJOS'],['work','DIARIO'],['config','CONFIGURAR']];
  for(const [id,label] of tabs){
    const b=document.createElement('button');b.type='button';b.textContent=label;b.dataset.target=id;
    b.addEventListener('click',()=>{
      if(id==='network') return openNetwork();
      if(id==='detail' && !$('fields')?.children.length) return showNotice('Busca una matrícula antes de consultar la ficha.');
      if(id==='work') { $('open-journal')?.click();return; }
      if(id==='weekly') {$('open-weekly')?.click();return;}
      if(id==='config') {$('home-config')?.click();return;}
      if(id==='detail') {$('tablet-network').hidden=true;document.querySelectorAll('#home,#weekly,#work,#config,#help,#feedback,#reminders').forEach(x=>x.hidden=true);$('detail').hidden=false;return;}
      if(id==='home') { $('tablet-network').hidden=true;$('top-back')?.click(); if($('detail').hidden) {screens.forEach(s=>{const e=$(s);if(e)e.hidden=s!=='home';});}return;}
    });
    nav.appendChild(b);
  }
  document.body.prepend(nav);
  const panel=document.createElement('section');panel.id='tablet-network';panel.hidden=true;panel.setAttribute('aria-label','Ping y red local del CT');
  document.body.appendChild(panel);
  function showNotice(message){const el=$('tablet-status');if(el)el.textContent=message;}
  function statusText(x){return x==='ok'?'CONECTADO':x==='failed'?'SIN RESPUESTA':'NO COMPROBADO';}
  function node(label,kind,status,caption){
    return '<div class="net-node '+kind+' status-'+status+'">'+sketch(kind)+'<strong>'+escape(label)+'</strong><small>'+escape(caption)+'</small><b style="color:'+(status==='ok'?'#168f39':'#c31129')+'">'+escape(statusText(status))+'</b></div>';
  }
  function render(){
    const valid=state.mango==='ok'&&state.tablet==='ok';
    let html='<h1>DIAGNÓSTICO DE RED DEL CT</h1><div class="net-summary" role="status" id="tablet-status">'
      +(valid?'MANGO PREPARADO PARA SSH.':'MANGO NO VERIFICADO: NO SE ATRIBUYEN FALLOS A EQUIPOS SIN MEDICIÓN.')+'</div>';
    html+='<div class="net-layout">'
      +node('TABLET / MÓVIL','tablet',state.tablet,'DISPOSITIVO LOCAL')
      +'<div>'+node('MANGO','mango',state.mango,'SSH: '+(state.mango==='ok'?'VERIFICADO': 'NO VERIFICADO'))+'</div>'
      +'<div>'+node('ROUTER DEL CT','router',state.router,'ENLACE LOCAL')+'</div>'
      +'<div class="net-equipment" aria-label="Equipos ordenados de arriba abajo">';
    for(const eq of state.equipment){
      const good=valid&&eq.status==='ok',failed=eq.status==='failed';
      html+='<div class="net-endpoints">'
        +'<article class="'+(good?'ok':'pending')+'"><span>'+escape(eq.name)+'</span><code>'
        +escape(eq.ip||'IP NO IMPORTADA')+'</code><small>'
        +escape(good?(typeof eq.rtt==='number'?eq.rtt+' MS':'CONECTADO'):failed?'SIN RESPUESTA':'NO COMPROBADO')
        +'</small></article></div>';
    }
    html+='</div></div><p class="net-actions">El gráfico muestra únicamente resultados verificados. Verde = éxito confirmado; rojo = fallo confirmado; sin verificar = pendiente. Los iconos en movimiento son una representación visual, no transferencias de archivos.</p>';
    panel.innerHTML=html;
    requestAnimationFrame(drawLinks);
  }
  // Draw connections on top of the actual responsive layout rather than hard-coded
  // lines. Every animation follows precisely the measured SVG path.
  function drawLinks(){
    if(panel.hidden)return;
    const board=panel.querySelector('.net-layout');
    if(!board)return;
    const old=board.querySelector('.net-wires');
    if(old)old.remove();
    const frame=board.getBoundingClientRect();
    const point=(el,edge)=>{
      const r=el.getBoundingClientRect();
      return {x:(edge==='right'?r.right:r.left)-frame.left,y:r.top+r.height/2-frame.top};
    };
    const tab=board.querySelector('.net-node.tablet');
    const mango=board.querySelector('.net-node.mango');
    const router=board.querySelector('.net-node.router');
    const endings=[...board.querySelectorAll('.net-equipment article')];
    if(!tab||!mango||!router||endings.length!==6)return;
    const NS='http://www.w3.org/2000/svg';
    const svg=document.createElementNS(NS,'svg');
    svg.setAttribute('class','net-wires');svg.setAttribute('viewBox',`0 0 ${frame.width} ${frame.height}`);
    svg.setAttribute('aria-hidden','true');
    function wire(a,b,ok,curved){
      const d=curved?
        `M ${a.x} ${a.y} C ${a.x+50} ${a.y}, ${b.x-50} ${b.y}, ${b.x} ${b.y}`:
        `M ${a.x} ${a.y} L ${b.x} ${b.y}`;
      const path=document.createElementNS(NS,'path');
      path.setAttribute('d',d);path.setAttribute('fill','none');
      path.setAttribute('stroke',ok?'#12a741':'#c81028');
      path.setAttribute('stroke-width','4.5');path.setAttribute('stroke-linecap','round');
      svg.appendChild(path);
      if(ok&&!window.matchMedia('(prefers-reduced-motion: reduce)').matches&&!document.hidden){
        const g=document.createElementNS(NS,'g');
        const paper=document.createElementNS(NS,'rect');
        paper.setAttribute('x','-7');paper.setAttribute('y','-9');paper.setAttribute('width','14');
        paper.setAttribute('height','18');paper.setAttribute('rx','2');
        paper.setAttribute('stroke','#147d34');paper.setAttribute('stroke-width','1.4');
        paper.setAttribute('fill','#d9ffe3');g.appendChild(paper);
        for(const y of [-3,2,6]){
          const line=document.createElementNS(NS,'path');
          line.setAttribute('d',`M -4 ${y} L 4 ${y}`);
          line.setAttribute('stroke','#147d34');line.setAttribute('stroke-width','1.2');g.appendChild(line);
        }
        const motion=document.createElementNS(NS,'animateMotion');
        motion.setAttribute('path',d);motion.setAttribute('dur','2.8s');
        motion.setAttribute('repeatCount','indefinite');g.appendChild(motion);
        svg.appendChild(g);
      }
    }
    wire(point(tab,'right'),point(mango,'left'),state.tablet==='ok'&&state.mango==='ok',false);
    wire(point(mango,'right'),point(router,'left'),state.mango==='ok'&&state.router==='ok',false);
    for(let i=0;i<6;i++){
      const good=state.tablet==='ok'&&state.mango==='ok'&&state.router==='ok'&&state.equipment[i].status==='ok';
      wire(point(router,'right'),point(endings[i],'left'),good,true);
    }
    board.prepend(svg);
  }
  window.addEventListener('resize',()=>{if(!panel.hidden)requestAnimationFrame(drawLinks)});
  function openNetwork(){
    if(window.CTWorkUI&&typeof window.CTWorkUI.canLeave==='function'&&!window.CTWorkUI.canLeave())return;
    if(window.CTWeeklyUI&&typeof window.CTWeeklyUI.canLeave==='function'&&!window.CTWeeklyUI.canLeave())return;
    for(const id of screens){if($(id))$(id).hidden=true;}
    panel.hidden=false;render();window.scrollTo(0,0);
  }
  // Authorized callers can provide measured values, not network targets to probe.
  // Avoid invented measurements and do not persist user-provided values.
  window.TCTabletView=Object.freeze({
    open:openNetwork,
    renderMeasurements:function(data){
      if(!data||typeof data!=='object')return;
      for(const key of ['tablet','mango','router']){
        if(['ok','failed','unchecked'].includes(data[key]))state[key]=data[key];
      }
      if(Array.isArray(data.equipment)){
        state.equipment=names.map((name,i)=>{
          const x=data.equipment.find(v=>v&&v.name===name)||{};
          const st=['ok','failed','unchecked'].includes(x.status)?x.status:'unchecked';
          const ip=typeof x.ip==='string'&&x.ip.length<=45?x.ip:'';
          const rtt=Number.isFinite(x.rtt)&&x.rtt>=0&&x.rtt<=60000?Math.round(x.rtt):null;
          return {name,status:st,ip,rtt};
        });
      }
      if(!panel.hidden)render();
    }
  });
  const observer=new MutationObserver(()=>{
    const active=panel.hidden?screens.find(id=>$(id)&&!$(id).hidden):'network';
    for(const b of nav.querySelectorAll('button')) {
      if(b.dataset.target===active)b.setAttribute('aria-current','page');
      else b.removeAttribute('aria-current');
    }
  });
  observer.observe(document.body,{subtree:true,attributes:true,attributeFilter:['hidden']});
  render();
  document.addEventListener('visibilitychange',()=>{
    document.body.classList.toggle('tablet-paused',document.hidden);
    if(!panel.hidden)requestAnimationFrame(drawLinks);
  });
})();
