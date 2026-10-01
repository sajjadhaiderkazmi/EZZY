// Lives only to clear the clipboard on a timer after something secret was copied. Offscreen
// pages cannot read the clipboard, so it simply overwrites it — the newest copy always wins,
// since each new copy resets the timer.

let timer = null;

function clearClipboard() {
  const buffer = document.getElementById('buffer');
  buffer.value = ' ';
  buffer.select();
  document.execCommand('copy');
  buffer.value = '';
}

chrome.runtime.onMessage.addListener((message) => {
  if (message?.type !== 'offscreen-clear') return;
  clearTimeout(timer);
  timer = setTimeout(clearClipboard, Math.max(5, message.seconds || 45) * 1000);
});
