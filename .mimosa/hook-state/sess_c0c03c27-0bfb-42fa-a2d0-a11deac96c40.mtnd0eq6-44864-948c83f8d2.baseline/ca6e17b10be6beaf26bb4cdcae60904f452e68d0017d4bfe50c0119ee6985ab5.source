 interface AvatarProps {
   name: string;
   size?: 'sm' | 'md' | 'lg';
 }
 
 const colors = [
   'bg-emerald-500', 'bg-blue-500', 'bg-violet-500', 'bg-amber-500',
   'bg-rose-500', 'bg-cyan-500', 'bg-orange-500', 'bg-teal-500',
 ];
 
 function hashColor(name: string): string {
   let hash = 0;
   for (let i = 0; i < name.length; i++) hash = name.charCodeAt(i) + ((hash << 5) - hash);
   return colors[Math.abs(hash) % colors.length];
 }
 
 const sizeMap = {
   sm: 'w-8 h-8 text-xs',
   md: 'w-10 h-10 text-sm',
   lg: 'w-14 h-14 text-lg',
 };
 
 export default function Avatar({ name, size = 'md' }: AvatarProps) {
   return (
     <div
       className={`${hashColor(name)} ${sizeMap[size]} rounded-full flex items-center justify-center text-white font-semibold shrink-0`}
       title={name}
     >
       {name.charAt(0).toUpperCase()}
     </div>
   );
 }
