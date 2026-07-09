package plana.replan.domain.item.dto;

/** 루틴 아이템 수정/삭제의 적용 범위. THIS = 이 날짜 회차만(override), ALL = 반복 전체(엄마 루틴). 투두(TODO) 아이템에는 사용하지 않는다. */
public enum ItemScope {
  THIS,
  ALL
}
