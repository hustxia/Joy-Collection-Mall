package com.mall.common.file;

import com.mall.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 文件落盘唯一实现（V4 抽取）。
 *
 * <p>此前落盘逻辑写在 {@link FileController} 私有方法里；V4 售后凭证上传需要
 * 会员身份入口（{@code /shop/upload}，会员 token 过不了 {@code /file/**} 的身份隔离），
 * 两处复制必然分叉，故抽为本服务。校验口径：
 * <ul>
 *   <li>大小：{@link FileProps#maxBytes()}（yml file.max-size-mb）</li>
 *   <li>扩展名：调用方传入白名单（后台沿用 file.allowed-exts 全量，C 端只放图片）</li>
 *   <li>落盘：yyyyMMdd/uuid.ext，写 sys_file 后返回记录，url 固定 /uploads/**（静态直链）</li>
 * </ul>
 */
@Service
public class FileStorageService {

    @Autowired
    private FileMapper fileMapper;

    @Autowired
    private FileProps props;

    /**
     * 保存上传文件：校验 → 落盘 → 写 sys_file。
     *
     * @param file        上传文件
     * @param createBy    操作人（后台为 username，会员为 "m:{id}" 形态的 username）
     * @param allowedExts 允许的小写扩展名（不带点）；传 null 用 file.allowed-exts 全量
     * @return 落库后的文件记录（url / id 已回填）
     */
    public FileRecord store(MultipartFile file, String createBy, List<String> allowedExts) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请选择要上传的文件");
        }
        if (file.getSize() > props.maxBytes()) {
            throw new BusinessException(400, "文件超过大小限制（" + props.getMaxSizeMb() + "MB）");
        }
        String original = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename());
        String ext = extOf(original);
        List<String> whitelist = allowedExts != null ? allowedExts : props.getAllowedExts();
        if (ext.isEmpty() || !whitelist.contains(ext)) {
            throw new BusinessException(400, "不支持的文件类型：." + (ext.isEmpty() ? "无扩展名" : ext));
        }
        String dateDir = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String storageName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        try {
            Path dir = Paths.get(props.getUploadDir(), dateDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(storageName));
        } catch (IOException e) {
            throw new BusinessException(500, "文件保存失败，请检查 upload-dir 配置与磁盘权限");
        }
        FileRecord record = new FileRecord();
        record.setOriginalName(original);
        record.setStorageName(storageName);
        record.setRelativePath(dateDir + "/" + storageName);
        record.setUrl("/uploads/" + dateDir + "/" + storageName);
        record.setContentType(file.getContentType());
        record.setFileSize(file.getSize());
        record.setCreateBy(createBy);
        fileMapper.insert(record);
        return record;
    }

    /** 小写扩展名（不带点，Locale.ROOT 防土耳其语 i 问题——原 FileController 注释保留） */
    private String extOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
