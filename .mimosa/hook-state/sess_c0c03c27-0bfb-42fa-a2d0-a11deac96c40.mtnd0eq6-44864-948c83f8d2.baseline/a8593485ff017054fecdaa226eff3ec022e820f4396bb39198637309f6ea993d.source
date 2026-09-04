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
   isPinned: boolean;
   status: number;
   userId?: string;
   createTime: string;
   aiReviewed: number;
   aiReviewScore: number;
   postType?: string;
   promptMetadata?: string;
   forkedFromId?: string;
   versionCount?: number;
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
   postCount: number;
 }

 export interface AiLogStats {
   totalReviews: number;
   approved: number;
   flagged: number;
   critical: number;
   high: number;
   medium: number;
   low: number;
   unknown: number;
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
