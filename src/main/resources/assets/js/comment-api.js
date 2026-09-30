(() => {
    class CommentRequestError extends Error {}

    function failureMessage(status) {
        if (status === 401) return 'Your admin session expired. Please sign in again.';
        if (status === 403) return 'This request was blocked. Refresh the page and try again.';
        if (status === 404) return 'This post or comment is no longer available.';
        if (status === 413) return 'Your comment is too large. Please shorten it and try again.';
        if (status === 429) return 'The comment limit has been reached. Please try again later.';
        if (status >= 500) return 'Comments are temporarily unavailable. Please try again later.';
        return 'The request could not be completed. Please try again.';
    }

    async function request(url, options = {}) {
        let response;
        try {
            response = await fetch(url, options);
        }
        catch (_) {
            throw new CommentRequestError('Could not reach the server. Check your connection and try again.');
        }

        if (response.redirected) {
            throw new CommentRequestError('Your session may have expired. Refresh the page and sign in again if needed.');
        }
        if (response.status === 204 && response.ok) return null;

        let data;
        try {
            data = await response.json();
        }
        catch (_) {
            // Proxies and framework errors may return an empty body or HTML.
        }

        if (!response.ok) {
            let message = typeof data?.error === 'string' && data.error.trim()
                ? data.error
                : failureMessage(response.status);

            if (Number.isFinite(data?.resetAt)) {
                message += ` Resets ${new Date(data.resetAt).toLocaleString()}.`;
            }
            throw new CommentRequestError(message);
        }

        if (data == null || typeof data !== 'object' || Array.isArray(data)) {
            throw new CommentRequestError('The server returned an unexpected response. Refresh the page to check whether your change was saved before trying again.');
        }
        return data;
    }

    function errorMessage(error, fallback) {
        return error instanceof CommentRequestError ? error.message : fallback;
    }

    window.CommentApi = {request, errorMessage};
})();
