# 반반컷

위 절반은 직접 찍은 행동, 아래 절반은 아토믹 투두리스트에서 그 할 일을 체크하는 화면 녹화.
둘을 할 일마다 한 쌍씩 이어서 1440×2560 릴스로 뽑는 안드로이드 앱.

## 쓰는 법
1. 컷 추가 → 위 영상과 앱 녹화를 갤러리에서 고른다.
2. 앱 녹화에서 체크한 순간을 슬라이더로 맞추고, 파란 칸(체크 장면)과 회색 칸(+1)을 맞춘다.
3. 영상 만들기 → 갤러리 `Movies/BanbanCut`에 저장된다.

## 바꾸기
- 컷 길이, 자막 위치, 훅 문구, 안전 영역: `app/src/main/assets/template.json`.
  저장소가 공개 상태면 앱이 켜질 때 main의 이 파일을 받아와서 APK를 새로 설치하지 않아도 바뀐다.
- 그 밖의 변경: main에 합치면 GitHub Actions가 서명된 APK를 Releases에 올리고, 앱이 새 버전을 알려준다.

## 빌드
`./gradlew assembleRelease`. 서명 키는 저장소 비밀 값 `KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
