#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""图标生成脚本：源文件缺失时跳过，不导致构建失败"""

import os
import sys

try:
    from PIL import Image
except ImportError:
    print("警告: 未安装 Pillow，跳过图标生成")
    sys.exit(0)

ICON_SIZES = {
    'mipmap-mdpi': 48,
    'mipmap-hdpi': 72,
    'mipmap-xhdpi': 96,
    'mipmap-xxhdpi': 144,
    'mipmap-xxxhdpi': 192,
}
CONTENT_RATIO = 0.75

def create_icon_with_padding(source_path, output_path, size):
    source = Image.open(source_path)
    content_size = int(size * CONTENT_RATIO)
    source_resized = source.resize((content_size, content_size), Image.Resampling.LANCZOS)
    result = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    offset = (size - content_size) // 2
    result.paste(source_resized, (offset, offset), source_resized if source_resized.mode == 'RGBA' else None)
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    result.save(output_path, 'PNG', optimize=True)
    print(f"已生成: {output_path} ({size}x{size})")

def main():
    source_icon = 'icon_original.png'
    if not os.path.exists(source_icon):
        # 尝试用已有 launcher 作为源
        for p in [
            'app/src/main/res/mipmap-xxxhdpi/ic_launcher.png',
            'app/src/main/res/mipmap-xxhdpi/ic_launcher.png',
            'app/src/main/res/mipmap-xhdpi/ic_launcher.png',
        ]:
            if os.path.exists(p):
                source_icon = p
                print(f"使用已有图标作为源: {p}")
                break
        else:
            print(f"警告: 找不到源图标，跳过生成（不中断构建）")
            sys.exit(0)

    res_dir = 'app/src/main/res'
    for folder, size in ICON_SIZES.items():
        output_path = os.path.join(res_dir, folder, 'ic_launcher.png')
        try:
            create_icon_with_padding(source_icon, output_path, size)
        except Exception as e:
            print(f"警告: 生成 {output_path} 失败: {e}")
    print("图标处理完成")
    sys.exit(0)

if __name__ == '__main__':
    main()
