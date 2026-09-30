(() => {
    const section = document.getElementById('comments');
    if (!section) return;
    const endpoint = `/api/posts/${encodeURIComponent(section.dataset.postId)}/comments`;
    const $ = id => document.getElementById(id);
    const form = $('comment-form');
    const records = new Map();
    let next = 0, replyToId = null, enabled = false, loading = false;
    const element = (tag, text, cls) => {
        const el = document.createElement(tag);
        if (text != null) el.textContent = text;
        if (cls) el.className = cls;
        return el;
    };
    function render() {
        const list = $('comment-list');
        list.replaceChildren();
        const threads = new Map();
        for (const comment of records.values()) {
            let thread = threads.get(comment.threadId);
            if (!thread) { thread = element('div', null, 'comment-thread'); threads.set(comment.threadId, thread); list.append(thread); }
            const entry = element('article', null, comment.id === comment.threadId ? 'comment' : 'comment comment-reply');
            entry.id = `comment-${comment.id}`;
            const meta = element('p', null, 'comment-meta');
            meta.append(element('strong', comment.deleted ? 'Comment deleted' : comment.name));
            if (comment.owner && !comment.deleted) meta.append(element('span', ' · Blog owner'));
            const link = element('a', new Date(comment.createdAt).toLocaleString());
            link.href = `#${entry.id}`;
            meta.append(document.createTextNode(' · '), link);
            entry.append(meta);
            if (comment.replyingTo) entry.append(element('small', `Replying to ${comment.replyingTo}`));
            entry.append(element('p', comment.deleted ? 'This comment has been deleted.' : comment.body, 'comment-body'));
            if (!comment.deleted && enabled) {
                const reply = element('button', 'Reply'); reply.type = 'button';
                reply.addEventListener('click', () => {
                    replyToId = comment.id;
                    $('comment-form-title').textContent = `Reply to ${comment.name}`;
                    $('comment-cancel-reply').hidden = false;
                    form.scrollIntoView({block: 'center', behavior: 'smooth'});
                    $('comment-body').focus();
                });
                entry.append(reply);
            }
            thread.append(entry);
        }
    }
    function cancelReply() {
        replyToId = null;
        $('comment-form-title').textContent = 'Leave a comment';
        $('comment-cancel-reply').hidden = true;
    }
    async function load(reset = false) {
        if (loading) return;
        loading = true;
        $('comment-more').disabled = true;
        try {
            if (reset) { next = 0; records.clear(); }
            do {
                const data = await CommentApi.request(`${endpoint}?offset=${next || 0}`, {cache: 'no-store'});
                data.comments.forEach(comment => records.set(comment.id, comment));
                next = data.next;
                enabled = data.enabled;
                $('comment-count').textContent = `(${data.total})`;
                form.hidden = !enabled;
                $('comments-closed').hidden = enabled;
                $('comment-load-status').textContent = data.total ? '' : 'No comments yet.';
                const anchor = location.hash.replace('#comment-', '');
                if (!location.hash.startsWith('#comment-') || records.has(anchor) || next == null) break;
            } while (next != null);
            render();
            $('comment-more').hidden = next == null;
            if (location.hash.startsWith('#comment-')) document.getElementById(location.hash.slice(1))?.scrollIntoView();
        }
        catch (error) {
            $('comment-load-status').textContent = CommentApi.errorMessage(
                error,
                'Could not load comments. Please refresh the page and try again.'
            );
        }
        finally { loading = false; $('comment-more').disabled = false; }
    }
    $('comment-more').addEventListener('click', () => load());
    $('comment-cancel-reply').addEventListener('click', cancelReply);
    form.addEventListener('submit', async event => {
        event.preventDefault();
        const submit = form.querySelector('[type="submit"]');
        submit.disabled = true;
        $('comment-form-status').textContent = 'Posting…';
        try {
            const data = await CommentApi.request(endpoint, {
                method: 'POST', headers: {'Content-Type': 'application/json', 'X-Comment-Request': '1'},
                body: JSON.stringify({name: $('comment-name').value, email: $('comment-email').value, body: $('comment-body').value, replyToId})
            });
            if (typeof data.id !== 'string') throw new Error('Invalid comment response');
            $('comment-body').value = '';
            cancelReply();
            $('comment-form-status').textContent = 'Your comment has been posted.';
            history.replaceState(null, '', `#comment-${data.id}`);
            await load(true);
        }
        catch (error) {
            $('comment-form-status').textContent = CommentApi.errorMessage(
                error,
                'Could not confirm your comment was posted. Refresh the page before trying again.'
            );
        }
        finally { submit.disabled = false; }
    });
    window.addEventListener('hashchange', () => { if (location.hash.startsWith('#comment-')) load(); });
    load();
})();
