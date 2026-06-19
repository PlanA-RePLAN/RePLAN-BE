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

messaging.onBackgroundMessage((payload) => {
  console.log("[firebase-messaging-sw.js] 백그라운드 메시지 수신:", payload);

  const notification = payload.notification || {};
  const title = notification.title || "replan 알림";
  const body  = notification.body  || "";

  self.registration.showNotification(title, {
    body,
    icon: "/icon-192.png", // 없으면 무시됨
  });
});
