(() => {
    const $ = id => document.getElementById(id);
    const toggle = $('comments-enabled');
    if (!toggle) return;
    let next = null, replyId = null;
    const posts = new Map([...$('comments-post-filter').options].map(option => [option.value, {title: option.text, slug: option.dataset.slug}]));
    const message = text => $('comments-admin-message').textContent = text;
    function showError(error) {
        message(CommentApi.errorMessage(
            error,
            'Could not complete the request. Refresh the page before trying again.'
        ));
    }

    async function request(path, method = 'GET', data) {
        return CommentApi.request(`/admin/comments${path}`, {
            method,
            cache: 'no-store',
            headers: {'Content-Type': 'application/json', 'X-Comment-Request': '1'},
            body: data == null ? undefined : JSON.stringify(data)
        });
    }
    async function status() {
        const data = await request('/status');
        toggle.checked = data.enabled; toggle.disabled = false;
        $('comments-admin-status').textContent = `${data.used} / ${data.limit} today. Resets ${new Date(data.resetAt).toUTCString()}. ${data.mailConfigured ? 'Email configured.' : 'Email not configured; notifications are pending.'} ${data.failedEmails} failed email jobs.`;
    }
    function elem(tag, text) { const el = document.createElement(tag); el.textContent = text; return el; }
    async function list(reset = false) {
        if (reset) { next = 0; $('comments-admin-list').replaceChildren(); }
        const data = await request(`?offset=${next || 0}&postId=${encodeURIComponent($('comments-post-filter').value)}`);
        next = data.next;
        for (const comment of data.comments) {
            const article = document.createElement('article'); article.className = 'admin-comment';
            const post = posts.get(comment.postId);
            const link = elem('a', post?.title || 'View post');
            if (post?.slug) link.href = `/post/${encodeURIComponent(post.slug)}#comment-${comment.id}`;
            article.append(link, elem('p', `${comment.deleted ? 'Comment deleted' : comment.name} · ${new Date(comment.createdAt).toLocaleString()}`));
            const body = elem('p', comment.body); body.style.whiteSpace = 'pre-wrap'; article.append(body);
            if (!comment.deleted) {
                const reply = elem('button', 'Reply'); reply.type = 'button';
                reply.onclick = () => { replyId = comment.id; $('admin-comment-reply').hidden = false; $('admin-comment-reply-title').textContent = `Reply to ${comment.name}`; $('admin-comment-body').focus(); };
                const remove = elem('button', 'Delete'); remove.type = 'button';
                remove.onclick = async () => {
                    if (!confirm('Delete this comment? Its replies will remain.')) return;
                    try { await request(`/${comment.id}`, 'DELETE'); await list(true); message('Comment deleted.'); }
                    catch (error) { showError(error); }
                };
                article.append(reply, remove);
            }
            $('comments-admin-list').append(article);
        }
        $('comments-admin-more').hidden = next == null;
    }
    async function refresh() { try { await status(); await list(true); } catch (error) { showError(error); } }
    toggle.onchange = async () => {
        toggle.disabled = true;
        try { await request('/settings', 'PUT', {enabled: toggle.checked}); message(toggle.checked ? 'Comments enabled.' : 'Comments closed. Existing comments remain visible.'); }
        catch (error) { toggle.checked = !toggle.checked; showError(error); }
        finally { toggle.disabled = false; }
    };
    $('comments-post-filter').onchange = refresh;
    $('comments-refresh').onclick = refresh;
    $('comments-admin-more').onclick = () => list().catch(error => showError(error));
    $('admin-comment-cancel').onclick = () => { replyId = null; $('admin-comment-reply').hidden = true; };
    $('admin-comment-reply').onsubmit = async event => {
        event.preventDefault();
        const button = event.target.querySelector('[type="submit"]'); button.disabled = true;
        try {
            await request(`/${replyId}/reply`, 'POST', {body: $('admin-comment-body').value});
            $('admin-comment-body').value = ''; $('admin-comment-reply').hidden = true; replyId = null;
            message('Reply posted.'); await refresh();
        } catch (error) { showError(error); }
        finally { button.disabled = false; }
    };
    refresh();
})();
