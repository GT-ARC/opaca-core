docker build -t feats-platform jiacpp-platform/.
docker tag feats-platform <registry>:5000/feats-platform
docker push <registry>:5000/feats-platform
