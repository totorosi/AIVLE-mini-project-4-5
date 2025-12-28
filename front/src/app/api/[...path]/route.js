import { NextResponse } from "next/server";

const BACKEND_BASE_URL = "http://10.0.2.205:8080";

async function proxy(req, ctx) {
  const { path = [] } = await ctx.params;
  const joined = Array.isArray(path) ? path.join("/") : "";

  const url = `${BACKEND_BASE_URL}/api/${joined}${req.nextUrl.search}`;

  const headers = new Headers(req.headers);
  headers.delete("host");
  headers.delete("content-length");

  const body =
    req.method === "GET" || req.method === "HEAD"
      ? undefined
      : await req.arrayBuffer();

  const res = await fetch(url, {
    method: req.method,
    headers,
    body,
  });

  const data = await res.arrayBuffer();

  return new NextResponse(data, {
    status: res.status,
    headers: res.headers,
  });
}

export const GET = proxy;
export const POST = proxy;
export const PUT = proxy;
export const PATCH = proxy;
export const DELETE = proxy;
