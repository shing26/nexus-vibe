 interface AvatarProps {
   name: string;
   size?: 'sm' | 'md' | 'lg';
 }
 
 const colors = [
   'bg-vibe-accent', 'bg-vibe-success', 'bg-vibe-warning', 'bg-vibe-high',
   'bg-vibe-danger', 'bg-vibe-muted',
 ];
 
 function hashColor(name: string): string {
   let hash = 0;
   for (let i = 0; i < name.length; i++) hash = name.charCodeAt(i) + ((hash << 5) - hash);
   return colors[Math.abs(hash) % colors.length];
 }
 
 const sizeMap = {
   sm: 'h-8 w-8 text-xs',
   md: 'h-10 w-10 text-sm',
   lg: 'h-14 w-14 text-lg',
 };
 
 export default function Avatar({ name, size = 'md' }: AvatarProps) {
   return (
     <div
       className={`${hashColor(name)} ${sizeMap[size]} flex shrink-0 items-center justify-center rounded-full font-semibold text-white`}
       title={name}
     >
       {name.charAt(0).toUpperCase()}
     </div>
   );
 }
