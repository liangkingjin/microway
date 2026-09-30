package com.wtlx.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * RAG 来源索引：记录每个来源（游记 / 攻略）的切片状态与内容指纹。
 * <p>
 * 作用：
 * <ol>
 *     <li>幂等：内容指纹未变且已成功时跳过，避免重复调用 embedding；</li>
 *     <li>可观测：失败原因与重试次数落库，便于补偿；</li>
 *     <li>删除：记录切片数量，删除时能算出全部切片 id。</li>
 * </ol>
 * 与主业务数据同库（MySQL），避免为一张元数据表再配一套 MyBatis 数据源。
 *
 * @author 金小梁
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("rag_source_index")
public class RagSourceIndex implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 来源类型：BLOG / GUIDE
     */
    private String docType;

    /**
     * 来源业务主键
     */
    private Long sourceId;

    /**
     * 内容指纹（md5），用于变更判定
     */
    private String contentHash;

    /**
     * 切片数量
     */
    private Integer chunkCount;

    /**
     * 状态：PENDING / SUCCESS / FAILED
     */
    private String status;

    /**
     * 失败原因
     */
    private String errorMsg;

    /**
     * 重试次数
     */
    private Integer retryCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
