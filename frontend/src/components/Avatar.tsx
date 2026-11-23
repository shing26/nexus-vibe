 interface AvatarProps {
   name: string;
   size?: 'sm' | 'md' | 'lg';
 }
 
 const sizeMap = {
   sm: 'h-8 w-8 text-xs',
   md: 'h-10 w-10 text-sm',
   lg: 'h-14 w-14 text-lg',
 };
 
/**
 * A person's initial on one neutral block. The old version hashed the name into
 * six saturated hues, which put a different colour on every commenter and made
 * the comment list read as a legend of categories that do not exist. Identity
 * comes from the letter; colour is reserved for the audit green.
 */
 export default function Avatar({ name, size = 'md' }: AvatarProps) {
   return (
     <div
      className={`${sizeMap[size]} flex shrink-0 items-center justify-center rounded-full bg-vibe-text font-medium text-vibe-bg`}
       title={name}
     >
       {name.charAt(0).toUpperCase()}
     </div>
   );
 }
