'use strict';
/* TELECONTROL TABLET: non-invasive Material-inspired motion.
   This is presentation only: no database, network or SSH operations. */
(function(){
  if(!document.body.classList.contains('tablet-industrial') || window.TabletMotionController)return;
  const ids=['home','detail','weekly','work','config','help','feedback','reminders','tablet-network'];
  const get=id=>document.getElementById(id);
  const prefersReduced=()=>window.matchMedia&&window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const easing='cubic-bezier(.2,0,0,1)';
  let current=visible(), lastClick=null, running=[], serial=0, pendingDetail=null;
  function visible(){
    if(get('tablet-network')&&!get('tablet-network').hidden)return 'tablet-network';
    for(const id of ids){
      const item=get(id);
      if(item&&!item.hidden){
        if(id==='weekly'&&get('weekly-detail')&&!get('weekly-detail').hidden)return 'weekly-detail';
        return id;
      }
    }
    return 'home';
  }
  function element(id){return id==='weekly-detail'?get('weekly-detail'):get(id);}
  function dismiss(){
    serial++;
    running.forEach(animation=>{try{animation.cancel();}catch(_){}});
    running=[];
    document.querySelectorAll('.tablet-motion-ghost,.tablet-motion-container').forEach(x=>x.remove());
    for(const id of ids){
      const e=get(id);if(e){e.style.opacity='';e.style.transform='';}
    }
    if(get('weekly-detail')){get('weekly-detail').style.opacity='';get('weekly-detail').style.transform='';}
  }
  function animate(el,frames,options){
    if(!el||!el.animate)return;
    const animation=el.animate(frames,Object.assign({fill:'none',easing},options));
    running.push(animation);
    animation.finished.catch(()=>{}).finally(()=>{running=running.filter(x=>x!==animation);});
    return animation;
  }
  function snapshot(target){
    const box=target&&target.getBoundingClientRect();
    if(!box||box.width<1||box.height<1)return null;
    return {left:box.left,top:box.top,width:box.width,height:box.height};
  }
  function sourceFrom(target){
    if(!target||!target.closest)return null;
    const tappable=target.closest('button,.weekly-row,.config-tile,.field');
    return snapshot(tappable||target);
  }
  function classified(from,to){
    if(from===to)return null;
    if(from==='detail'&&to==='tablet-network'||from==='tablet-network'&&to==='detail')return 'shared-x';
    if(from==='weekly'&&to==='weekly-detail'||from==='weekly-detail'&&to==='weekly')return 'container';
    if(from==='home'&&to==='detail')return 'container';
    if(from==='config'||to==='config'||to==='help'||to==='feedback'||to==='reminders')return 'shared-z';
    if(to==='work'||from==='work'||to==='weekly'||from==='weekly'||to==='home'||from==='home')return 'fade-through';
    return 'shared-x';
  }
  function direction(from,to){return (from==='tablet-network'&&to==='detail')||(from==='weekly-detail'&&to==='weekly')||(to==='home');}
  function reduce(el){if(el){el.style.opacity='';el.style.transform='';}}
  function dispatch(from,to){
    if(document.hidden||prefersReduced()||typeof Element.prototype.animate!=='function'){dismiss();return;}
    const incoming=element(to);
    if(!incoming)return;
    const outgoing=lastClick&&lastClick.from===from?lastClick.fromEl:null;
    const origin=lastClick&&lastClick.from===from?lastClick.box:null;
    const kind=classified(from,to),back=direction(from,to),shift=Math.min(window.innerWidth*.12,130);
    dismiss();
    const token=serial;
    if(!kind)return;
    if(kind==='container'&&origin){
      const end=snapshot(incoming);
      if(end){
        const layer=document.createElement('div');
        layer.className='tablet-motion-container';layer.setAttribute('aria-hidden','true');
        layer.style.cssText='position:fixed;pointer-events:none;z-index:210;top:0;left:0;background:#fff;border:2px solid #c40b22;box-shadow:0 8px 35px #0002;';
        document.body.appendChild(layer);
        const startBox=back?end:origin,finalBox=back?origin:end;
        animate(layer,[
          {transform:`translate(${startBox.left}px,${startBox.top}px)`,width:startBox.width+'px',height:startBox.height+'px',borderRadius:'18px',opacity:'.95'},
          {transform:`translate(${finalBox.left}px,${finalBox.top}px)`,width:finalBox.width+'px',height:finalBox.height+'px',borderRadius:back?'18px':'6px',opacity:'.45'}
        ],{duration:330,fill:'forwards'});
        animate(incoming,[{opacity:0},{opacity:0,offset:.32},{opacity:1}],{duration:330});
        setTimeout(()=>{if(token===serial)layer.remove();},370);
        return;
      }
    }
    if(kind==='container')return dispatchFallback(incoming,shift);
    if(kind==='fade-through'){
      if(outgoing&&outgoing!==incoming&&!outgoing.hidden){
        animate(outgoing,[{opacity:1},{opacity:0}],{duration:110});
      }
      animate(incoming,[{opacity:0,transform:'scale(.97)',offset:0},{opacity:0,transform:'scale(.97)',offset:.32},{opacity:1,transform:'scale(1)',offset:1}],{duration:310});
    } else if(kind==='shared-x'){
      animate(incoming,[{opacity:0,transform:`translateX(${back?-shift:shift}px)`},{opacity:1,transform:'translateX(0)'}],{duration:280});
    } else if(kind==='shared-z'){
      animate(incoming,[{opacity:0,transform:back?'scale(1.045)':'scale(.94)'},{opacity:1,transform:'scale(1)'}],{duration:255});
    }
  }
  function dispatchFallback(el,shift){
    animate(el,[{opacity:0,transform:`translateX(${shift}px) scale(.96)`},{opacity:1,transform:'none'}],{duration:280});
  }
  // Store only geometry from known navigation actions, never user input text.
  const navigationIds=new Set(['submit','open-weekly','open-journal','home-config','top-back','weekly-back','weekly-detail-back','config-back','work-back','open-notes','detail-config','detail-help','detail-feedback','config-back']);
  document.addEventListener('click',event=>{
    const target=event.target;
    if(!target||!target.closest)return;
    const button=target.closest('button');
    const nav=button&&button.closest('#tablet-topnav');
    const weekly=target.closest('.weekly-row,.weekly-open');
    const config=target.closest('.config-tile');
    if(!((button&&navigationIds.has(button.id))||nav||weekly||config))return;
    const old=visible();
    const source=sourceFrom(weekly||config||button);
    lastClick={from:old,box:source,fromEl:element(old)};
    if(old==='tablet-network'){
      // An original Android-back event may be dispatched without a click.
      pendingDetail='tablet-network';
    }
  },true);
  // After existing application handlers set hidden flags, perform presentation only.
  const observer=new MutationObserver(()=>{
    const next=visible();
    if(next===current)return;
    const from=current;current=next;
    dispatch(from,next);
    if(next!=='tablet-network')pendingDetail=null;
  });
  observer.observe(document.body,{subtree:true,attributes:true,attributeFilter:['hidden']});
  // Some weekly views use nested show/hide with the parent always visible.
  document.addEventListener('visibilitychange',()=>{if(document.hidden)dismiss();});
  window.addEventListener('pagehide',dismiss);
  window.addEventListener('orientationchange',dismiss);
  window.TabletMotionController=Object.freeze({
    effects:['fade-through','shared-x','shared-z','container'],
    transitionFor:classified,
    current:()=>current,
    reduced:prefersReduced,
    cancel:dismiss
  });
})();
