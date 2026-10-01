import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { gzipSync } from 'node:zlib';
import { createHash } from 'node:crypto';

const revision = process.argv[2];
if (!/^[a-f0-9]{40}$/.test(revision || '')) throw new Error('pass a full uBlock source revision');
const dest = new URL('../core/src/main/resources/adblock/', import.meta.url);
const temp = await fs.mkdtemp(path.join(os.tmpdir(), 'pane-resources-'));
const fetchText = async url => {
    const response = await fetch(url);
    if (!response.ok) throw new Error(`${response.status}: ${url}`);
    return response;
};
try {
    const tree = await (await fetchText(`https://api.github.com/repos/gorhill/uBlock/git/trees/${revision}?recursive=1`)).json();
    const files = tree.tree.filter(p => p.type === 'blob' && (p.path.startsWith('src/js/resources/') || p.path.startsWith('src/web_accessible_resources/') || p.path === 'src/js/redirect-resources.js' || p.path === 'LICENSE.txt'));
    const downloaded = new Map();
    const download = name => {
        if (downloaded.has(name)) return downloaded.get(name);
        const pending = (async () => {
            const data = Buffer.from(await (await fetchText(`https://raw.githubusercontent.com/gorhill/uBlock/${revision}/${name}`)).arrayBuffer());
            const file = path.join(temp, name);
            await fs.mkdir(path.dirname(file), { recursive: true });
            await fs.writeFile(file, data);
            if (name.startsWith('src/js/') && name.endsWith('.js')) {
                for (const match of data.toString().matchAll(/(?:from|import)\s+['"](\.[^'"]+)['"]/g)) {
                    await download(path.posix.normalize(path.posix.join(path.posix.dirname(name), match[1])));
                }
            }
        })();
        downloaded.set(name, pending);
        return pending;
    };
    await Promise.all(files.map(f => download(f.path)));
    await fs.writeFile(path.join(temp, 'package.json'), '{"type":"module"}');
    const { builtinScriptlets } = await import(pathToFileURL(path.join(temp, 'src/js/resources/scriptlets.js')));
    const { default: redirects } = await import(pathToFileURL(path.join(temp, 'src/js/redirect-resources.js')));
    const scriptlets = builtinScriptlets.map(r => ({
        name: r.name, aliases: r.aliases || [], fn: r.fn.name, source: r.fn.toString(),
        dependencies: r.dependencies || [], trusted: !!r.requiresTrust, priority: r.priority || 0,
    }));
    const mime = { js: 'application/javascript', html: 'text/html', css: 'text/css', gif: 'image/gif', png: 'image/png', mp3: 'audio/mpeg', mp4: 'video/mp4', xml: 'text/xml', json: 'application/json', txt: 'text/plain' };
    const resources = [];
    for (const [name, details] of redirects) {
        const data = await fs.readFile(path.join(temp, 'src/web_accessible_resources', name));
        resources.push({ name, aliases: [details.alias || []].flat(), mime: mime[path.extname(name).slice(1)] || 'text/plain', data: data.toString('base64'), trusted: !!details.requiresTrust });
        if (details.data === 'text' && name.endsWith('.js')) {
            scriptlets.push({ name, aliases: [details.alias || []].flat().map(a => a.endsWith('.js') ? a : `${a}.js`), fn: '', source: data.toString(), dependencies: [], trusted: !!details.requiresTrust });
        }
    }
    await fs.mkdir(dest, { recursive: true });
    const notices = new Set();
    for (const name of downloaded.keys()) {
        if (!name.endsWith('.js')) continue;
        for (const line of (await fs.readFile(path.join(temp, name), 'utf8')).split('\n')) {
            if (line.includes('Copyright ')) notices.add(line.trim());
        }
    }
    const catalogue = { revision, notice: [...notices].join('\n') + '\nhttps://github.com/gorhill/uBlock\nGPL-3.0-or-later', scriptlets, redirects: resources };
    await fs.writeFile(new URL('resources.json', dest), JSON.stringify(catalogue) + '\n');
    await fs.copyFile(path.join(temp, 'LICENSE.txt'), new URL('ublock-license.txt', dest));
    const assets = new URL('../app/src/main/assets/filters/', import.meta.url);
    await fs.mkdir(assets, { recursive: true });
    const catalogueSource = await fs.readFile(new URL('../app/src/main/java/app/pane/browser/engine/FilterLists.kt', import.meta.url), 'utf8');
    const hash = createHash('sha256');
    for (const [, id, name, url] of catalogueSource.matchAll(/Info\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,?\s*\)/g)) {
        const text = await (await fetchText(url)).text();
        if (text.trimStart().startsWith('<')) throw new Error(`not a filter list: ${url}`);
        const source = `! Title: ${name}\n! Source: ${url}\n! Bundled: ${new Date().toISOString().slice(0, 10)}\n` + text;
        hash.update(id).update(source);
        await fs.writeFile(new URL(`${id}.txt.gz`, assets), gzipSync(source));
    }
    await fs.writeFile(new URL('revision.txt', assets), hash.digest('hex') + '\n');
    await fs.writeFile(new URL('peter-lowe-license.txt', assets), await (await fetchText('https://pgl.yoyo.org/license/')).text());
    console.log(`${scriptlets.length} scriptlet resources, ${resources.length} redirects, ${Buffer.byteLength(JSON.stringify(catalogue)) + 1} bytes`);
} finally {
    await fs.rm(temp, { recursive: true, force: true });
}
