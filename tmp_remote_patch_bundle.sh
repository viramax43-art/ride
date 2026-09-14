python3 - <<'PY'
from pathlib import Path

path = Path('/var/lib/docker/volumes/rideminiapp_frontend_dist/_data/assets/index-cMcSvF0J.js')
text = path.read_text()

replacements = [
    (
        'function yS({field:e,language:t,value:n,fileEntry:a,previewUrl:o,previewFileName:s,previewContentType:d,previewSizeBytes:u,error:f,disabled:p,isUploading:g,onValueChange:x,onFileSelect:y,onFileClear:w,onFileError:b}){',
        'function yS({field:e,language:t,value:n,fileEntry:a,previewUrl:o,previewDataUrl:r,previewFileName:s,previewContentType:d,previewSizeBytes:u,error:f,disabled:p,isUploading:g,onValueChange:x,onFileSelect:y,onFileClear:w,onFileError:b}){',
    ),
    (
        'const M=!!a,E=!!(o&&s),R=(a==null?void 0:a.fileName)??s??k,U=(a==null?void 0:a.sizeBytes)??u??0,T=(a==null?void 0:a.contentType)??d??"",q=a?vc(a.fileUrl,Es()):o??"",Y=!!q&&Dv(T,R);',
        'const M=!!a,E=!!(o&&s),R=(a==null?void 0:a.fileName)??s??k,U=(a==null?void 0:a.sizeBytes)??u??0,T=(a==null?void 0:a.contentType)??d??"",q=r??o??(a?vc(a.fileUrl,Es()):""),Y=!!q&&Dv(T,R);',
    ),
    (
        'var I,F,Z,X;return i.jsx(yS,{field:B,language:t.language,value:f[B.id]??"",fileEntry:g[B.id]??null,previewUrl:((I=y[B.id])==null?void 0:I.previewUrl)??null,previewFileName:((F=y[B.id])==null?void 0:F.fileName)??null,previewContentType:((Z=y[B.id])==null?void 0:Z.contentType)??null,previewSizeBytes:((X=y[B.id])==null?void 0:X.sizeBytes)??null,error:C[B.id],disabled:P,isUploading:!!b[B.id],onValueChange:oe=>{p(D=>({...D,[B.id]:oe})),k(D=>{const ne={...D};return delete ne[B.id],ne})},onFileSelect:oe=>G(B.id,oe),onFileError:oe=>k(D=>({...D,[B.id]:oe})),onFileClear:()=>{x(oe=>{const D={...oe};return delete D[B.id],D}),w(oe=>{const D={...oe};return delete D[B.id],D})}},B.id)})',
        'var I,F,Z,X,J;return i.jsx(yS,{field:B,language:t.language,value:f[B.id]??"",fileEntry:g[B.id]??null,previewUrl:((I=y[B.id])==null?void 0:I.previewUrl)??null,previewDataUrl:((J=y[B.id])==null?void 0:J.previewDataUrl)??null,previewFileName:((F=y[B.id])==null?void 0:F.fileName)??null,previewContentType:((Z=y[B.id])==null?void 0:Z.contentType)??null,previewSizeBytes:((X=y[B.id])==null?void 0:X.sizeBytes)??null,error:C[B.id],disabled:P,isUploading:!!b[B.id],onValueChange:oe=>{p(D=>({...D,[B.id]:oe})),k(D=>{const ne={...D};return delete ne[B.id],ne})},onFileSelect:oe=>G(B.id,oe),onFileError:oe=>k(D=>({...D,[B.id]:oe})),onFileClear:()=>{x(oe=>{const D={...oe};return delete D[B.id],D}),w(oe=>{const D={...oe};return delete D[B.id],D})}},B.id)})',
    ),
    (
        'const oe=await Ck(I);x(X=>({...X,[B]:{objectKey:oe.objectKey,fileName:oe.fileName,contentType:oe.contentType,sizeBytes:oe.sizeBytes,fileUrl:oe.fileUrl}})),w(X=>{const re={...X};return delete re[B],re}),k(X=>{const re={...X};return delete re[B],re})}',
        'const oe=await Ck(I);x(X=>({...X,[B]:{objectKey:oe.objectKey,fileName:oe.fileName,contentType:oe.contentType,sizeBytes:oe.sizeBytes,fileUrl:oe.fileUrl}})),k(X=>{const re={...X};return delete re[B],re})}',
    ),
]

for old, new in replacements:
    if old not in text:
        raise SystemExit(f'missing snippet: {old[:120]}')
    text = text.replace(old, new, 1)

path.write_text(text)
PY

grep -n -m 3 'previewDataUrl\\|previewUrl:o,previewDataUrl:r\\|delete re\\[B\\],re' /var/lib/docker/volumes/rideminiapp_frontend_dist/_data/assets/index-cMcSvF0J.js
