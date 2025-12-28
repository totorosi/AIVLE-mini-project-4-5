'use client';

import api from "@/app/api/apiClient";

/**
 * 로그인 요청 API
 */
export async function loginRequest(id, pw) {
  if (!id.trim() || !pw.trim()) {
    throw new Error('아이디와 비밀번호를 모두 입력해주세요.');
  }

  // baseURL, withCredentials, interceptor 자동 적용
  const loginRes = await api.post('/auth/login', { id, pw });

  const result = loginRes.data;

  // Authorization 헤더에서 accessToken 추출
  const authHeader = loginRes.headers['authorization'];
  const accessToken =
    authHeader && authHeader.startsWith('Bearer ')
      ? authHeader.replace('Bearer ', '')
      : null;

  return {
    result,
    accessToken,
  };
}
