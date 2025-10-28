import React, { useState, useEffect } from 'react';
import styled from 'styled-components';

const Container = styled.div`
  max-width: 1200px;
  margin: 0 auto;
  padding: ${props => props.theme.spacing.xl} ${props => props.theme.spacing.lg};
`;

const Header = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: ${props => props.theme.spacing.xl};
  
  @media (max-width: 768px) {
    flex-direction: column;
    gap: ${props => props.theme.spacing.md};
    align-items: stretch;
  }
`;

const Title = styled.h1`
  color: ${props => props.theme.colors.text};
  font-size: ${props => props.theme.typography.h2.fontSize};
  font-weight: ${props => props.theme.typography.h2.fontWeight};
  margin: 0;
`;

const WriteButton = styled.button`
  background: ${props => props.theme.colors.primary};
  color: white;
  border: none;
  padding: ${props => props.theme.spacing.md} ${props => props.theme.spacing.lg};
  border-radius: ${props => props.theme.borderRadius.lg};
  font-size: ${props => props.theme.typography.body1.fontSize};
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  gap: ${props => props.theme.spacing.sm};
  
  &:hover {
    background: ${props => props.theme.colors.primaryDark};
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(255, 126, 54, 0.3);
  }
`;

const CategoryTabs = styled.div`
  display: flex;
  gap: ${props => props.theme.spacing.md};
  margin-bottom: ${props => props.theme.spacing.xl};
  flex-wrap: wrap;
`;

const CategoryTab = styled.button`
  background: ${props => props.active ? props.theme.colors.primary : props.theme.colors.surface};
  color: ${props => props.active ? 'white' : props.theme.colors.text};
  border: 1px solid ${props => props.active ? props.theme.colors.primary : props.theme.colors.border};
  padding: ${props => props.theme.spacing.sm} ${props => props.theme.spacing.md};
  border-radius: ${props => props.theme.borderRadius.full};
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: ${props => props.theme.typography.body2.fontSize};
  
  &:hover {
    background: ${props => props.active ? props.theme.colors.primaryDark : props.theme.colors.surfaceHover};
    transform: translateY(-1px);
  }
`;

const PostList = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${props => props.theme.spacing.md};
`;

const PostCard = styled.div`
  background: ${props => props.theme.colors.surface};
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: ${props => props.theme.borderRadius.lg};
  padding: ${props => props.theme.spacing.lg};
  transition: all 0.3s ease;
  cursor: pointer;
  
  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 16px ${props => props.theme.colors.shadow};
    border-color: ${props => props.theme.colors.primary};
  }
`;

const PostHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: ${props => props.theme.spacing.sm};
`;

const PostTitle = styled.h3`
  color: ${props => props.theme.colors.text};
  font-size: ${props => props.theme.typography.h4.fontSize};
  font-weight: ${props => props.theme.typography.h4.fontWeight};
  margin: 0;
  line-height: 1.4;
  flex: 1;
  margin-right: ${props => props.theme.spacing.sm};
`;

const CategoryBadge = styled.span`
  background: ${props => props.theme.colors.primaryLight};
  color: white;
  padding: ${props => props.theme.spacing.xs} ${props => props.theme.spacing.sm};
  border-radius: ${props => props.theme.borderRadius.sm};
  font-size: ${props => props.theme.typography.caption.fontSize};
  font-weight: 600;
`;

const PostContent = styled.p`
  color: ${props => props.theme.colors.textSecondary};
  font-size: ${props => props.theme.typography.body2.fontSize};
  line-height: 1.5;
  margin: ${props => props.theme.spacing.sm} 0;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
`;

const PostFooter = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: ${props => props.theme.spacing.md};
  padding-top: ${props => props.theme.spacing.sm};
  border-top: 1px solid ${props => props.theme.colors.borderLight};
`;

const AuthorInfo = styled.div`
  display: flex;
  align-items: center;
  gap: ${props => props.theme.spacing.sm};
`;

const AuthorAvatar = styled.div`
  width: 32px;
  height: 32px;
  border-radius: ${props => props.theme.borderRadius.full};
  background: ${props => props.theme.colors.primary};
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-weight: 600;
  font-size: 14px;
`;

const AuthorName = styled.span`
  color: ${props => props.theme.colors.text};
  font-size: ${props => props.theme.typography.body2.fontSize};
  font-weight: 500;
`;

const PostStats = styled.div`
  display: flex;
  gap: ${props => props.theme.spacing.md};
  color: ${props => props.theme.colors.textLight};
  font-size: ${props => props.theme.typography.caption.fontSize};
`;

const LoadingMessage = styled.div`
  text-align: center;
  padding: ${props => props.theme.spacing.xxl};
  color: ${props => props.theme.colors.textSecondary};
  font-size: ${props => props.theme.typography.body1.fontSize};
`;

const CommunityBoard = () => {
  const [posts, setPosts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [activeCategory, setActiveCategory] = useState('ALL');

  // 임시 데이터
  useEffect(() => {
    setTimeout(() => {
      setPosts([
        {
          idx: 1,
          title: '강아지 산책 매너에 대해 이야기해요',
          content: '요즘 산책하다 보면 매너가 부족한 경우를 종종 봅니다. 우리 모두 서로 배려하는 산책 문화를 만들어가요!',
          category: 'TIP',
          user: { username: '멍멍맘', location: '강남구' },
          createdAt: '2024-11-01T10:30:00',
          comments: 12,
          likes: 24
        },
        {
          idx: 2,
          title: '우리 동네 애견카페 추천해주세요',
          content: '서초구 근처에 강아지와 함께 갈 수 있는 좋은 카페 있나요? 분위기 좋고 강아지 친화적인 곳으로...',
          category: 'QUESTION',
          user: { username: '골든러버', location: '서초구' },
          createdAt: '2024-10-31T15:20:00',
          comments: 8,
          likes: 15
        },
        {
          idx: 3,
          title: '고양이 털 관리 팁 공유합니다',
          content: '장모종 고양이 키우시는 분들을 위한 털 관리 꿀팁들을 정리해봤어요. 브러싱부터 목욕까지!',
          category: 'TIP',
          user: { username: '냥이집사', location: '송파구' },
          createdAt: '2024-10-30T18:45:00',
          comments: 6,
          likes: 31
        },
        {
          idx: 4,
          title: '반려동물 응급처치 교육 후기',
          content: '지난 주말에 참석한 반려동물 응급처치 교육이 정말 유익했어요. 모든 반려인분들께 추천드립니다!',
          category: 'INFO',
          user: { username: '안전제일', location: '마포구' },
          createdAt: '2024-10-29T12:15:00',
          comments: 4,
          likes: 18
        }
      ]);
      setLoading(false);
    }, 1000);
  }, []);

  const categories = [
    { key: 'ALL', label: '전체', icon: '📋' },
    { key: 'TIP', label: '꿀팁', icon: '💡' },
    { key: 'QUESTION', label: '질문', icon: '❓' },
    { key: 'INFO', label: '정보', icon: '📢' },
    { key: 'STORY', label: '일상', icon: '📖' }
  ];

  const filteredPosts = activeCategory === 'ALL' 
    ? posts 
    : posts.filter(post => post.category === activeCategory);

  const getCategoryLabel = (category) => {
    const cat = categories.find(c => c.key === category);
    return cat ? cat.label : category;
  };

  const formatDate = (dateString) => {
    const date = new Date(dateString);
    const now = new Date();
    const diff = now - date;
    const hours = Math.floor(diff / (1000 * 60 * 60));
    
    if (hours < 1) return '방금 전';
    if (hours < 24) return `${hours}시간 전`;
    return date.toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' });
  };

  if (loading) {
    return <LoadingMessage>커뮤니티 게시글을 불러오는 중...</LoadingMessage>;
  }

  return (
    <Container>
      <Header>
        <Title>💬 커뮤니티</Title>
        <WriteButton>
          <span>✏️</span>
          글쓰기
        </WriteButton>
      </Header>

      <CategoryTabs>
        {categories.map(category => (
          <CategoryTab
            key={category.key}
            active={activeCategory === category.key}
            onClick={() => setActiveCategory(category.key)}
          >
            <span style={{ marginRight: '4px' }}>{category.icon}</span>
            {category.label}
          </CategoryTab>
        ))}
      </CategoryTabs>

      <PostList>
        {filteredPosts.map(post => (
          <PostCard key={post.idx}>
            <PostHeader>
              <PostTitle>{post.title}</PostTitle>
              <CategoryBadge>{getCategoryLabel(post.category)}</CategoryBadge>
            </PostHeader>
            
            <PostContent>{post.content}</PostContent>
            
            <PostFooter>
              <AuthorInfo>
                <AuthorAvatar>
                  {post.user.username.charAt(0)}
                </AuthorAvatar>
                <div>
                  <AuthorName>{post.user.username}</AuthorName>
                  <div style={{ fontSize: '12px', color: 'var(--text-light)' }}>
                    📍 {post.user.location}
                  </div>
                </div>
              </AuthorInfo>
              <PostStats>
                <span>💬 {post.comments}</span>
                <span>❤️ {post.likes}</span>
                <span>{formatDate(post.createdAt)}</span>
              </PostStats>
            </PostFooter>
          </PostCard>
        ))}
      </PostList>
    </Container>
  );
};

export default CommunityBoard;
