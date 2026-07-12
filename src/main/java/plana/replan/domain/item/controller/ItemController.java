package plana.replan.domain.item.controller;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import plana.replan.domain.item.dto.ItemCompleteRequestDto;
import plana.replan.domain.item.dto.ItemContentRequestDto;
import plana.replan.domain.item.dto.ItemDeleteRequestDto;
import plana.replan.domain.item.dto.ItemDetailResponseDto;
import plana.replan.domain.item.dto.ItemKind;
import plana.replan.domain.item.dto.ItemOrderRequestDto;
import plana.replan.domain.item.dto.ItemPinRequestDto;
import plana.replan.domain.item.dto.ItemResponseDto;
import plana.replan.domain.item.dto.ItemSubTodoCreateRequestDto;
import plana.replan.domain.item.dto.ItemSubTodoDeleteRequestDto;
import plana.replan.domain.item.dto.ItemSubTodoUpdateRequestDto;
import plana.replan.domain.item.service.ItemFacadeService;
import plana.replan.global.common.ApiResult;

@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class ItemController implements ItemControllerDocs {

  private final ItemFacadeService itemFacadeService;

  @Override
  @GetMapping
  public ResponseEntity<ApiResult<List<ItemResponseDto>>> getItems(
      @AuthenticationPrincipal Long userId,
      @RequestParam(defaultValue = "all") String filter,
      @RequestParam(defaultValue = "priority") String sort,
      @RequestParam(required = false) LocalDate date) {
    return ResponseEntity.ok(ApiResult.ok(itemFacadeService.getItems(userId, filter, sort, date)));
  }

  @Override
  @GetMapping("/detail")
  public ResponseEntity<ApiResult<ItemDetailResponseDto>> getItemDetail(
      @AuthenticationPrincipal Long userId,
      @RequestParam ItemKind kind,
      @RequestParam(required = false) Long todoId,
      @RequestParam(required = false) Long routineId,
      @RequestParam(required = false) LocalDate date) {
    return ResponseEntity.ok(
        ApiResult.ok(itemFacadeService.getDetail(userId, kind, todoId, routineId, date)));
  }

  @Override
  @PatchMapping("/complete")
  public ResponseEntity<ApiResult<Void>> completeItem(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody ItemCompleteRequestDto request) {
    itemFacadeService.complete(userId, request);
    return ResponseEntity.ok(ApiResult.ok());
  }

  @Override
  @PatchMapping("/pin")
  public ResponseEntity<ApiResult<Void>> pinItem(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody ItemPinRequestDto request) {
    itemFacadeService.pin(userId, request);
    return ResponseEntity.ok(ApiResult.ok());
  }

  @Override
  @PatchMapping("/order")
  public ResponseEntity<ApiResult<Void>> reorderItem(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody ItemOrderRequestDto request) {
    itemFacadeService.reorder(userId, request);
    return ResponseEntity.ok(ApiResult.ok());
  }

  @Override
  @PatchMapping("/content")
  public ResponseEntity<ApiResult<Void>> updateItemContent(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody ItemContentRequestDto request) {
    itemFacadeService.updateContent(userId, request);
    return ResponseEntity.ok(ApiResult.ok());
  }

  @Override
  @DeleteMapping
  public ResponseEntity<ApiResult<Void>> deleteItem(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody ItemDeleteRequestDto request) {
    itemFacadeService.delete(userId, request);
    return ResponseEntity.ok(ApiResult.ok());
  }

  @Override
  @PostMapping("/subtodos")
  public ResponseEntity<ApiResult<Void>> addItemSubTodo(
      @AuthenticationPrincipal Long userId,
      @Valid @RequestBody ItemSubTodoCreateRequestDto request) {
    itemFacadeService.addSubTodo(userId, request);
    return ResponseEntity.ok(ApiResult.ok());
  }

  @Override
  @PatchMapping("/subtodos")
  public ResponseEntity<ApiResult<Void>> updateItemReservedSubTodo(
      @AuthenticationPrincipal Long userId,
      @Valid @RequestBody ItemSubTodoUpdateRequestDto request) {
    itemFacadeService.updateReservedSubTodo(userId, request);
    return ResponseEntity.ok(ApiResult.ok());
  }

  @Override
  @DeleteMapping("/subtodos")
  public ResponseEntity<ApiResult<Void>> deleteItemReservedSubTodo(
      @AuthenticationPrincipal Long userId,
      @Valid @RequestBody ItemSubTodoDeleteRequestDto request) {
    itemFacadeService.deleteReservedSubTodo(userId, request);
    return ResponseEntity.ok(ApiResult.ok());
  }
}
