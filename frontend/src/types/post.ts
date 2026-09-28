 export interface PostPageVo {
   id: string;
   title: string;
   content: string;
   summary: string;
   authorName: string;
   categoryName: string;
  viewCount: number;
  likeCount: number;
  commentCount: number;
  status: number;
   userId?: string;
   createTime: string;
   aiReviewed: number;
   aiReviewScore: number;
   postType?: string;
   promptMetadata?: string;
   forkedFromId?: string;
   versionCount?: number;
   /** Whether the logged-in viewer has liked this post; null/absent when anonymous */
   likedByMe?: boolean | null;
 }

 export interface PromptVersion {
   id: string;
   postId: string;
   version: number;
   branch: string;
   title: string;
   content: string;
   promptMetadata?: string;
   changeNote?: string;
   createdBy: string;
   authorName: string;
   createTime: string;
 }

 export interface Channel {
   id: number;
   slug: string;
   name: string;
   description: string;
   icon?: string;
 }

 export interface Comment {
   id: string;
   postId: string;
   authorName: string;
   content: string;
   userId: string;
   createTime: string;
 }

 export interface PageResponse<T> {
   list: T[];
   total: string;
   page: number;
   size: number;
   pages: number;
 }

 export interface AiLog {
   id: string;
   postId: string;
   postTitle: string;
   reviewer: string;
   severity: string | null;
   isApproved: number;
   status?: 'completed' | 'unavailable';
   createdAt: string;
 }

export interface ChannelStats {
  id: number;
  slug: string;
  /** long on the server, so JacksonConfig sends it as a string */
  postCount: string;
}

/**
 * /agent-logs/stats hands out a Map of long counters, and every Long leaves the
 * server as a string. These were typed as numbers for three rounds and only
 * ever worked because String.prototype.toLocaleString exists.
 */
export interface AiLogStats {
  totalReviews: string;
  approved: string;
  flagged: string;
  critical: string;
  high: string;
  medium: string;
  low: string;
  unknown: string;
}

 export interface AiReviewDetail {
   postId: string;
   reviewer: string;
   score: number | null;
   severity: string;
   isApproved: boolean | number;
   codeQuality: string | string[];
   securityConcerns: string | string[];
   optimizationSuggestions: string | string[];
   reviewedAt: string;
 }

 export interface ProfileStats {
   posts: number;
   comments: number;
   likesReceived: number;
   avgAiScore: number | null;
   forks: number;
   versions: number;
 }

 export interface ActivityItem {
   id: string | number;
   type: string;
   postId: string | number;
   title: string;
   createdAt: string;
 }

 export interface UserProfileSummary {
   id: string | number;
   username: string;
   nickname?: string;
   avatar?: string;
   bio?: string;
   createTime?: string;
   stats: ProfileStats;
   recentActivity: ActivityItem[];
 }
