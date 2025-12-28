package com.example.back.service;

import com.example.back.DTO.*;
import com.example.back.entity.Book;
import com.example.back.entity.Category;
import com.example.back.entity.User;
import com.example.back.repository.BookRepository;
import com.example.back.repository.CategoryRepository;
import com.example.back.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

@Service
@Slf4j
@RequiredArgsConstructor
public class BookService {

    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final BookCoverStorageService bookCoverStorageService;
    private final S3Client s3Client;

    public BookListResponse getBooks(int page, int size) {
        Sort sort = Sort.by(Sort.Direction.DESC, "id");
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Book> result = bookRepository.findAll(pageable);
        return BookListResponse.from(result);
    }

    public BookListResponse searchBooksByTitle(String title, int page, int size) {
        log.info("도서 제목 검색 서비스 시작: title={}, page={}, size={}", title, page, size);

        if (title == null || title.isBlank()) {
            log.warn("도서 제목 검색 실패 - 잘못된 검색어: title 비어 있음");
            throw new IllegalArgumentException("검색어(title)가 올바르지 않습니다.");
        }

        Sort sort = Sort.by(Sort.Direction.DESC, "id");
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Book> result = bookRepository.findByTitleContainingIgnoreCase(title.trim(), pageable);

        log.info("도서 제목 검색 서비스 완료: title={}, totalElements={}", title, result.getTotalElements());
        return BookListResponse.from(result);
    }

    public BookDetailResponse getBookDetail(Long bookId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("도서를 찾을 수 없습니다."));
        return BookDetailResponse.from(book);
    }

    @Transactional
    @SuppressWarnings("null")
    public BookCreateResponse createBook(String userId, BookCreateRequest req) {
        log.info("도서 등록 서비스 시작: userId={}, title={}", userId, req.getTitle());

        // 1) 필수 값 검증
        if (req.getTitle() == null || req.getTitle().isBlank()
                || req.getDescription() == null || req.getDescription().isBlank()
                || req.getContent() == null || req.getContent().isBlank()) {
            log.warn("도서 등록 실패 - 잘못된 요청 데이터: title/description/content 비어 있음");
            throw new IllegalArgumentException("도서 정보가 올바르지 않습니다.");
        }

        if (req.getCategoryId() == null) {
            log.warn("도서 등록 실패 - categoryId null");
            throw new IllegalArgumentException("카테고리 정보가 올바르지 않습니다.");
        }

        // 2) 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("도서 등록 실패 - 사용자 조회 실패: userId={}", userId);
                    return new RuntimeException("사용자 정보를 찾을 수 없습니다.");
                });

        // 3) 카테고리 조회
        Category category = categoryRepository.findById(req.getCategoryId())
                .orElseThrow(() -> {
                    log.warn("도서 등록 실패 - 카테고리 조회 실패: categoryId={}", req.getCategoryId());
                    return new RuntimeException("카테고리 정보를 찾을 수 없습니다.");
                });

        // 4) Book 생성 + 저장(먼저 bookId 확보)
        Book book = new Book();
        book.setUser(user);
        book.setCategoryId(category);
        book.setTitle(req.getTitle());
        book.setDescription(req.getDescription());
        book.setContent(req.getContent());

        Book saved = bookRepository.save(book);
        log.info("도서 등록 서비스 - Book 저장 완료: bookId={}", saved.getId());

        // 5) 이미지 URL 들어온 경우 → S3 업로드 → DB에 최종 URL 저장
        if (req.getImageUrl() != null && !req.getImageUrl().isBlank()) {

            String publicUrl = bookCoverStorageService.saveCoverFromUrl(
                    req.getImageUrl(),
                    saved.getId()
            );

            if (publicUrl == null) {
                //  테스트용: 업로드 실패해도 도서 생성은 진행
                log.warn("이미지 업로드 실패(테스트 진행): imageUrl={}", req.getImageUrl());
            } else {
                saved.setImageUrl(publicUrl);

                //  중요: imageUrl 반영을 확실히 DB에 저장
                bookRepository.save(saved);

                log.info("도서 등록 서비스 - imageUrl 저장 완료: bookId={}, imageUrl={}", saved.getId(), publicUrl);
            }
        }

        log.info("도서 등록 서비스 완료: bookId={}", saved.getId());
        return new BookCreateResponse(saved.getId());
    }

    @Transactional
    @SuppressWarnings("null")
    public BookUpdateResponse updateBook(String userId, Long bookId, BookUpdateRequest req) {
        log.info("도서 수정 서비스 시작: userId={}, bookId={}, title={}", userId, bookId, req.getTitle());

        // 1) 필수 값 검증
        if (req.getTitle() == null || req.getTitle().isBlank()
                || req.getDescription() == null || req.getDescription().isBlank()
                || req.getContent() == null || req.getContent().isBlank()) {
            log.warn("도서 수정 실패 - 잘못된 요청 데이터: title/description/content 비어 있음");
            throw new IllegalArgumentException("도서 정보가 올바르지 않습니다.");
        }

        if (req.getCategoryId() == null) {
            log.warn("도서 수정 실패 - categoryId null");
            throw new IllegalArgumentException("카테고리 정보가 올바르지 않습니다.");
        }

        // 2) 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("도서 수정 실패 - 사용자 조회 실패: userId={}", userId);
                    return new RuntimeException("사용자 정보를 찾을 수 없습니다.");
                });

        // 3) 도서 조회
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> {
                    log.warn("도서 수정 실패 - 도서 조회 실패: bookId={}", bookId);
                    return new RuntimeException("도서 정보를 찾을 수 없습니다.");
                });

        // 4) 소유자 검증
        if (!book.getUser().getId().equals(user.getId())) {
            log.warn("도서 수정 실패 - 권한 없음: 요청 userId={}, 도서 소유자={}", userId, book.getUser().getId());
            throw new RuntimeException("본인이 등록한 도서만 수정할 수 있습니다.");
        }

        // 5) 카테고리 조회
        Category category = categoryRepository.findById(req.getCategoryId())
                .orElseThrow(() -> {
                    log.warn("도서 수정 실패 - 카테고리 조회 실패: categoryId={}", req.getCategoryId());
                    return new RuntimeException("카테고리 정보를 찾을 수 없습니다.");
                });

        // 6) 기본 필드 수정
        book.setCategoryId(category);
        book.setTitle(req.getTitle());
        book.setDescription(req.getDescription());
        book.setContent(req.getContent());
        book.setUpdated_at(java.time.LocalDateTime.now());

        Book saved = bookRepository.save(book);
        log.info("도서 수정 기본 정보 저장 완료: bookId={}, imageUrl(초기)={}", saved.getId(), saved.getImageUrl());

        // 7) imageUrl 들어온 경우 → S3 업로드 → DB에 최종 URL 저장
        if (req.getImageUrl() != null && !req.getImageUrl().isBlank()) {

            String publicUrl = bookCoverStorageService.saveCoverFromUrl(
                    req.getImageUrl(),
                    saved.getId()
            );

            if (publicUrl == null) {
                log.warn("도서 수정 실패 - 유효하지 않은 이미지 URL: {}", req.getImageUrl());
                throw new IllegalArgumentException("유효하지 않은 이미지 URL입니다.");
            }

            saved.setImageUrl(publicUrl);

            //  중요: imageUrl 반영을 확실히 DB에 저장
            bookRepository.save(saved);

            log.info("도서 수정 서비스 - imageUrl 저장 완료: bookId={}, imageUrl={}", saved.getId(), publicUrl);
        }

        log.info("도서 수정 서비스 완료: bookId={}, 최종 imageUrl={}", saved.getId(), saved.getImageUrl());
        return new BookUpdateResponse(saved.getId());
    }

    @Transactional
    public DeleteBookResponse deleteBook(String userId, Long bookId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("도서를 찾을 수 없습니다."));

        if (!book.getUser().getId().equals(userId)) {
            throw new RuntimeException("본인이 등록한 도서만 삭제할 수 있습니다.");
        }

        String imageUrl = book.getImageUrl();
        if (imageUrl != null && !imageUrl.isBlank()) {
            String key = imageUrl.substring(imageUrl.indexOf(".amazonaws.com/") + ".amazonaws.com/".length());
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket("user045-book")
                    .key(key)
                    .build());
        }

        bookRepository.delete(book);
        return new DeleteBookResponse(bookId, 1);
    }
}
