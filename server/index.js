// Список разрешенных путей для проксирования на api.github.com
const ALLOWED_PATHS = [
  "/user",
  "/gists"
];

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    // 1. Быстрый ответ на CORS (preflight-запросы)
    if (request.method === "OPTIONS") {
      return new Response(null, {
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Methods": "GET, POST, PUT, PATCH, DELETE, OPTIONS",
          "Access-Control-Allow-Headers": "Content-Type, Authorization, Accept",
          "Access-Control-Max-Age": "86400", // Кэширование preflight на 24 часа
        },
      });
    }

    // 2. БЕЗОПАСНАЯ АВТОРИЗАЦИЯ GITHUB (Только для роута /auth/token)
    if (url.pathname === "/auth/token") {
      if (request.method !== "POST") {
        return new Response("Method Not Allowed", { status: 405 });
      }
      try {
        const body = await request.json();
        const code = body.code;

        if (!code) {
          return new Response(JSON.stringify({ error: "Missing authorization_code" }), {
            status: 400,
            headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
          });
        }

        const tokenResponse = await fetch("https://github.com/login/oauth/access_token", {
          method: "POST",
          headers: {
            "Accept": "application/json",
            "Content-Type": "application/json"
          },
          body: JSON.stringify({
            client_id: env.GH_CLIENT_ID,
            client_secret: env.GH_CLIENT_SECRET,
            code: code
          })
        });

        const tokenData = await tokenResponse.json();

        return new Response(JSON.stringify(tokenData), {
          status: 200,
          headers: {
            "Content-Type": "application/json",
            "Access-Control-Allow-Origin": "*"
          }
        });
      } catch (e) {
        return new Response(JSON.stringify({ error: e.toString() }), {
          status: 500,
          headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
        });
      }
    }

    // 3. ЗАЩИТА ОТ БОТОВ: Проверяем, входит ли запрашиваемый путь в белый список
    const isAllowed = ALLOWED_PATHS.some(allowedPath =>
      url.pathname === allowedPath || url.pathname.startsWith(allowedPath + "/")
    );

    if (!isAllowed) {
      // Боты мгновенно получают от воркера 403, не нагружая твой аккаунт и GitHub
      return new Response(JSON.stringify({ error: "Access Denied: Path not allowed on proxy" }), {
        status: 403,
        headers: {
          "Content-Type": "application/json",
          "Access-Control-Allow-Origin": "*"
        }
      });
    }

    // 4. ОСНОВНОЙ ПРОКСИ: Перенаправляем разрешенные запросы на api.github.com
    const targetUrl = new URL(request.url);
    targetUrl.hostname = "api.github.com";

    // Создаем новые заголовки, удаляя старый Host (иначе GitHub выдаст ошибку)
    const newHeaders = new Headers(request.headers);
    newHeaders.delete("Host");

    // GitHub API требует наличие User-Agent
    if (!newHeaders.has("User-Agent")) {
       newHeaders.set("User-Agent", "Rupoop-Proxy");
    }

    const proxyRequest = new Request(targetUrl, {
      method: request.method,
      headers: newHeaders,
      body: request.body,
      redirect: "follow"
    });

    try {
      const response = await fetch(proxyRequest);
      const newResponse = new Response(response.body, response);
      newResponse.headers.set("Access-Control-Allow-Origin", "*");
      return newResponse;
    } catch (e) {
      return new Response(JSON.stringify({ error: "Failed to fetch from GitHub API" }), {
        status: 502,
        headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
      });
    }
  }
};
