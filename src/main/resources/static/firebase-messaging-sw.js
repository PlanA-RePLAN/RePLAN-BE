// Firebase 백그라운드 메시지 수신 서비스 워커 (로컬 테스트 전용)
// ================================================================
// 주의: 아래 firebaseConfig 는 fcm-test.html 의 것과 반드시 동일해야 합니다.
// ================================================================

// ── TODO: 아래 값을 실제 Firebase 프로젝트 설정으로 채워주세요. ──────────────
// Firebase Console → ⚙️ 프로젝트 설정 → 일반(General)
//   → "내 앱" 섹션의 웹 앱 → SDK 설정 및 구성(firebaseConfig 블록)
const firebaseConfig = {
  apiKey:            "__FILL_ME__",
  authDomain:        "__FILL_ME__",
  projectId:         "__FILL_ME__",
  storageBucket:     "__FILL_ME__",
  messagingSenderId: "__FILL_ME__",
  appId:             "__FILL_ME__",
};
// ─────────────────────────────────────────────────────────────────────────────

importScripts("https://www.gstatic.com/firebasejs/10.12.2/firebase-app-compat.js");
importScripts("https://www.gstatic.com/firebasejs/10.12.2/firebase-messaging-compat.js");

firebase.initializeApp(firebaseConfig);

const messaging = firebase.messaging();

// 주의: 메시지에 notification 페이로드가 있으면 브라우저가 자동으로 1개를 표시한다.
// 여기서 showNotification 을 또 호출하면 '2개씩' 뜨므로, 표시는 하지 않고 로그만 남긴다.
// (데이터 처리만 필요하면 여기서 한다)
messaging.onBackgroundMessage((payload) => {
  console.log("[firebase-messaging-sw.js] 백그라운드 메시지 수신:", payload);
});
