"use client";

import { useEffect, useState } from "react";
import Pagination from "@mui/material/Pagination";
import "./css/books.css";

// 🔐 인증 필요한 API용 (유지)
import api from "@/app/api/apiClient";

// 🌐 인증 불필요 API용 (추가)
import publicApi from "@/app/api/publicApiClient";

export default function Home() {
  const [books, setBooks] = useState([]);
  const [totalItems, setTotalItems] = useState(0);
  const [loading, setLoading] = useState(true);

  const [page, setPage] = useState(1);
  const size = 28;

  const [hasToken, setHasToken] = useState(false);

  useEffect(() => {
    const token = localStorage.getItem("accessToken");
    setHasToken(!!token);
  }, []);

  // ==========================
  // ✅ 도서 목록 조회 (JWT 없음)
  // ==========================
  async function fetchBooks(currentPage) {
    try {
      setLoading(true);

      console.log(
        "📡 요청 URL:",
        `/api/books?page=${currentPage}&size=${size}`
      );

      // ✅ public API 클라이언트 사용
      const res = await publicApi.get("/books", {
        params: {
          page: currentPage,
          size,
        },
      });

      const data = res.data?.data;
      const list = data?.books ?? [];

      setBooks(list);
      setTotalItems(data?.totalItems ?? 0);
    } catch (err) {
      console.error("❌ 도서 목록 불러오기 실패:", err);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    fetchBooks(page);
  }, [page]);

  const totalPages = Math.ceil(totalItems / size);

  return (
    <main className="container py-5 home-container">
      <div className="d-flex flex-wrap align-items-center justify-content-between gap-2 mb-4">
        <h2 className="section-title m-0">📚 도서 목록</h2>

        <div className="flex justify-end items-center gap-3">
          {hasToken && (
            <button
              className="badge rounded-pill text-bg-light border books-count-badge"
              onClick={() => (window.location.href = "/new_post")}
            >
              도서 등록
            </button>
          )}

          <span className="badge rounded-pill text-bg-light border books-count-badge">
            {loading ? "불러오는 중..." : `총 ${totalItems}권`}
          </span>
        </div>
      </div>

      {loading && (
        <div className="d-flex align-items-center gap-2 text-secondary">
          <div className="spinner-border spinner-border-sm" role="status" />
          <span>불러오는 중...</span>
        </div>
      )}

      {!loading && books.length === 0 && (
        <div className="empty-state">
          <div className="empty-icon">📭</div>
          <div className="empty-title">표시할 도서가 없습니다.</div>
          <div className="empty-desc">잠시 후 다시 시도해 주세요.</div>
        </div>
      )}

      {!loading && books.length > 0 && (
        <div className="row g-4">
          {books.map((book) => (
            <div
              key={book.bookId}
              className="col-12 col-sm-6 col-md-4 col-lg-3"
            >
              <div
                className="book-card border shadow-sm"
                role="button"
                onClick={() =>
                  (window.location.href = `/post_view/${book.bookId}`)
                }
              >
                <div className="book-thumb">
                  <img
                    src={book.imageUrl}
                    alt={book.title || "제목 없음"}
                    className="book-image"
                    loading="lazy"
                    onError={(e) => {
                      e.currentTarget.style.display = "none";
                      e.currentTarget.parentElement?.classList.add(
                        "thumb-fallback"
                      );
                    }}
                  />
                </div>

                <div className="card-body py-2">
                  <h5 className="card-title book-title mb-1">
                    {book.title || "제목 없음"}
                  </h5>

                  <span className="badge bg-secondary ms-2">
                    {book.category || "미분류"}
                  </span>
                </div>

                <div className="card-footer bg-transparent border-0 pt-0 pb-2">
                  <span className="read-more">자세히 보기 →</span>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {!loading && totalItems > 0 && (
        <div className="pagination-container d-flex justify-content-center">
          <Pagination
            count={totalPages}
            page={page}
            onChange={(e, value) => setPage(value)}
            color="primary"
            shape="rounded"
            size="large"
          />
        </div>
      )}
    </main>
  );
}
